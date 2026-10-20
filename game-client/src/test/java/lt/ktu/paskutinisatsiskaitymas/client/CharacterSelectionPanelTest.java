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
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import lt.ktu.paskutinisatsiskaitymas.protocol.Hello;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CharacterSelectionPanelTest {
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
                    assertTrue(card.getHeight() >= 200, "Selected indicator must fit");
                    assertTrue(card.getFontMetrics(card.getFont().deriveFont(java.awt.Font.BOLD, 13f))
                            .stringWidth(card.getText()) < card.getWidth() - 16);
                    assertNotNull(AssetManager.getInstance().getImage(card.getText() + "_PORTRAIT"));
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

    private static void layout(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container nested) {
                layout(nested);
            }
        }
    }
}
