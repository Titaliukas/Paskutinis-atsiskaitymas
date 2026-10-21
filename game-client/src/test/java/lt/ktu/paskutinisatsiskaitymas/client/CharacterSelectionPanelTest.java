package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.JToggleButton;
import javax.swing.JSlider;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import lt.ktu.paskutinisatsiskaitymas.protocol.Hello;
import lt.ktu.paskutinisatsiskaitymas.protocol.StatAllocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CharacterSelectionPanelTest {
    @Test
    void slidersPreviewOneDragClampToBudgetAndSupportKeyboardAndSilentUpdates() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            CharacterSelectionPanel panel = new CharacterSelectionPanel();
            List<JSlider> sliders = sliders(panel);
            List<String> changes = new ArrayList<>();
            panel.setStatChangeListener((stat, value) -> changes.add(stat + ":" + value));
            JSlider health = sliders.get(0);
            JSlider speed = sliders.get(1);
            health.setValueIsAdjusting(true);
            health.setValue(90);
            health.setValue(80);
            health.setValue(75);
            assertTrue(changes.isEmpty(), "Drag only previews until release");
            health.setValueIsAdjusting(false);
            assertEquals(List.of("HEALTH:75"), changes);
            speed.setValue(150);
            assertEquals(125, speed.getValue(), "Only the 25 freed points may be spent");
            assertEquals(List.of("HEALTH:75", "SPEED:125"), changes);
            speed.setValue(150);
            assertEquals(2, changes.size(), "A blocked increase creates no command");
            health.getActionMap().get("negativeUnitIncrement")
                    .actionPerformed(new ActionEvent(health, 0, ""));
            assertEquals(70, health.getValue());
            speed.getActionMap().get("positiveUnitIncrement")
                    .actionPerformed(new ActionEvent(speed, 0, ""));
            assertEquals(130, speed.getValue());
            int count = changes.size();
            panel.setStats(new StatAllocation(50, 100, 150));
            assertEquals(List.of(50, 100, 150), sliders.stream().map(JSlider::getValue).toList());
            assertEquals(count, changes.size());
            panel.setEnabled(false);
            sliders.forEach(slider -> assertFalse(slider.isEnabled()));
            health.setValue(75);
            assertEquals(50, health.getValue());
            assertEquals(count, changes.size());
            panel.setEnabled(true);
            panel.setStats(StatAllocation.DEFAULT);
            health.setValueIsAdjusting(true);
            health.setValue(80);
            health.setValue(100);
            health.setValueIsAdjusting(false);
            assertEquals(count, changes.size(), "Returning to the starting value creates no command");
        });
    }

    @Test
    void undoSynchronizesSlidersAndCardWithoutRecursiveCommands() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            CharacterSelectionPanel panel = new CharacterSelectionPanel();
            CharacterSelection selection = new CharacterSelection();
            CommandHistory history = new CommandHistory();
            panel.setStatChangeListener((stat, value) -> history.execute(new ChangeStatCommand(selection, stat, value)));
            panel.setSelectionListener(character -> {
                history.execute(new SelectCharacterCommand(selection, character));
                panel.setStats(selection.stats());
            });
            sliders(panel).get(0).setValue(75);
            cards(panel).get(1).doClick(0);
            assertEquals(100, sliders(panel).get(0).getValue());
            history.undo();
            panel.setSelectedCharacter(selection.selectedCharacter());
            panel.setStats(selection.stats());
            assertEquals("PATASIJUS", selected(cards(panel)));
            assertEquals(75, sliders(panel).get(0).getValue());
            history.undo();
            panel.setStats(selection.stats());
            assertEquals(100, sliders(panel).get(0).getValue());
            assertFalse(history.canUndo());
        });
    }
    @Test
    void clicksAndKeyboardActivationChooseExactlyOneCharacterAndIgnoreRepeats() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            CharacterSelectionPanel panel = new CharacterSelectionPanel();
            List<JToggleButton> cards = cards(panel);
            List<String> selected = new ArrayList<>();
            panel.setSelectionListener(selected::add);
            assertEquals(Hello.CHARACTERS, cards.stream().map(JToggleButton::getText).toList());
            assertEquals("PATASIJUS", selected(cards));
            cards.get(0).doClick(0);
            assertTrue(selected.isEmpty());
            cards.get(2).doClick(0);
            cards.get(2).doClick(0);
            assertEquals(List.of("JURGELINIS"), selected);
            assertEquals("JURGELINIS", selected(cards));

            JToggleButton keyboardCard = cards.get(1);
            Object press = keyboardCard.getInputMap().get(KeyStroke.getKeyStroke("pressed SPACE"));
            Object release = keyboardCard.getInputMap().get(KeyStroke.getKeyStroke("released SPACE"));
            assertNotNull(press);
            assertNotNull(release);
            keyboardCard.getActionMap().get(press).actionPerformed(new ActionEvent(keyboardCard, 0, ""));
            keyboardCard.getActionMap().get(release).actionPerformed(new ActionEvent(keyboardCard, 0, ""));
            assertEquals("VACIJUS", selected(cards));
            assertEquals(List.of("JURGELINIS", "VACIJUS"), selected);
        });
    }

    @Test
    void disabledCardsAndProgrammaticSelectionDoNotEmitActions() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            CharacterSelectionPanel panel = new CharacterSelectionPanel();
            List<JToggleButton> cards = cards(panel);
            List<String> selected = new ArrayList<>();
            panel.setSelectionListener(selected::add);
            panel.setSelectedCharacter("GOGIS");
            assertEquals("GOGIS", selected(cards));
            assertTrue(selected.isEmpty());
            panel.setEnabled(false);
            for (JToggleButton card : cards) {
                assertFalse(card.isEnabled());
                card.doClick(0);
            }
            assertEquals("GOGIS", selected(cards));
            assertTrue(selected.isEmpty());
            panel.setEnabled(true);
            cards.get(1).doClick(0);
            assertEquals(List.of("VACIJUS"), selected);
            assertThrows(IllegalArgumentException.class, () -> panel.setSelectedCharacter("UNKNOWN"));
            assertThrows(IllegalArgumentException.class, () -> panel.setSelectedCharacter(null));
            assertEquals("VACIJUS", selected(cards));
        });
    }

    @Test
    void undoRestoresTheCardWithoutRecordingAnotherSelection() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            CharacterSelectionPanel panel = new CharacterSelectionPanel();
            CharacterSelection selection = new CharacterSelection();
            CommandHistory history = new CommandHistory();
            panel.setSelectionListener(character -> history.execute(new SelectCharacterCommand(selection, character)));
            List<JToggleButton> cards = cards(panel);
            cards.get(1).doClick(0);
            cards.get(3).doClick(0);
            history.undo();
            panel.setSelectedCharacter(selection.selectedCharacter());
            assertEquals("VACIJUS", selected(cards));
            history.undo();
            panel.setSelectedCharacter(selection.selectedCharacter());
            assertEquals("PATASIJUS", selected(cards));
            assertFalse(history.canUndo());
        });
    }

    @Test
    void portraitsAndNamesFitAtNormalAndMinimumSizes() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (int width : new int[]{960, 640}) {
                CharacterSelectionPanel panel = new CharacterSelectionPanel();
                panel.setSize(width, width == 960 ? 540 : 360);
                layout(panel);
                for (JToggleButton card : cards(panel)) {
                    assertTrue(card.getWidth() >= 112, "Portrait and name must fit");
                    assertTrue(card.getHeight() >= 160, "Selected indicator must fit");
                    assertTrue(card.getFontMetrics(card.getFont().deriveFont(java.awt.Font.BOLD, 13f))
                            .stringWidth(card.getText()) < card.getWidth() - 16);
                    assertNotNull(AssetManager.getInstance().getImage(card.getText() + "_PORTRAIT"));
                }
                for (JSlider slider : sliders(panel)) {
                    var bounds = SwingUtilities.convertRectangle(slider.getParent(), slider.getBounds(), panel);
                    assertTrue(bounds.x >= 0 && bounds.y >= 0 && bounds.x + bounds.width <= width
                            && bounds.y + bounds.height <= panel.getHeight(), "Sliders must fit");
                    assertTrue(slider.getWidth() >= 200, "Slider remains usable at minimum size");
                }
                BufferedImage image = new BufferedImage(width, panel.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = image.createGraphics();
                try {
                    panel.paint(graphics);
                } finally {
                    graphics.dispose();
                }
                try {
                    Files.createDirectories(Path.of("target"));
                    ImageIO.write(image, "png", Path.of("target/character-picker-" + width + ".png").toFile());
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            }
        });
    }

    private static String selected(List<JToggleButton> cards) {
        List<JToggleButton> selected = cards.stream().filter(JToggleButton::isSelected).toList();
        assertEquals(1, selected.size());
        return selected.getFirst().getText();
    }

    private static List<JToggleButton> cards(Container parent) {
        List<JToggleButton> result = new ArrayList<>();
        for (Component component : parent.getComponents()) {
            if (component instanceof JToggleButton button) {
                result.add(button);
            } else if (component instanceof Container container) {
                result.addAll(cards(container));
            }
        }
        return result;
    }

    private static List<JSlider> sliders(Container parent) {
        List<JSlider> result = new ArrayList<>();
        for (Component component : parent.getComponents()) {
            if (component instanceof JSlider slider) {
                result.add(slider);
            } else if (component instanceof Container container) {
                result.addAll(sliders(container));
            }
        }
        return result;
    }

    private static void layout(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container nested) {
                layout(nested);
            }
        }
    }
}
