package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import lt.ktu.paskutinisatsiskaitymas.protocol.Hello;

/** EDT-owned portrait picker. Programmatic selection never emits a user action. */
final class CharacterSelectionPanel extends JPanel {
    private static final Color BACKGROUND = new Color(17, 23, 34);
    private static final Color ACCENT = new Color(109, 186, 255);
    private final Map<String, JToggleButton> cards = new LinkedHashMap<>();
    private Consumer<String> selectionListener = character -> { };
    private String selectedCharacter = Hello.DEFAULT_CHARACTER;

    CharacterSelectionPanel() {
        super(new GridBagLayout());
        setBackground(BACKGROUND);
        setPreferredSize(new Dimension(960, 540));
        setMinimumSize(new Dimension(640, 360));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(0, 24, 10, 24);
        JLabel title = new JLabel("Choose your character", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
        title.setForeground(Color.WHITE);
        constraints.gridy = 0;
        add(title, constraints);
        JLabel hint = new JLabel("Select a portrait, then click Connect above.", SwingConstants.CENTER);
        hint.setForeground(new Color(180, 193, 210));
        constraints.gridy = 1;
        constraints.insets = new Insets(0, 24, 24, 24);
        add(hint, constraints);

        JPanel row = new JPanel(new GridLayout(1, Hello.CHARACTERS.size(), 12, 0));
        row.setOpaque(false);
        ButtonGroup group = new ButtonGroup();
        AssetManager assets = AssetManager.getInstance();
        for (String character : Hello.CHARACTERS) {
            JToggleButton card = new CharacterCard(character, assets.getImage(character + "_PORTRAIT"));
            cards.put(character, card);
            group.add(card);
            row.add(card);
            card.addActionListener(event -> {
                if (isEnabled() && !selectedCharacter.equals(character)) {
                    setSelectedCharacter(character);
                    selectionListener.accept(character);
                }
            });
        }
        constraints.gridy = 2;
        constraints.insets = new Insets(0, 24, 0, 24);
        add(row, constraints);
        setSelectedCharacter(selectedCharacter);
    }

    void setSelectionListener(Consumer<String> listener) {
        selectionListener = Objects.requireNonNull(listener, "listener");
    }

    void setSelectedCharacter(String character) {
        JToggleButton card = cards.get(character);
        if (card == null) {
            throw new IllegalArgumentException("Unknown character");
        }
        selectedCharacter = character;
        card.setSelected(true);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        cards.values().forEach(card -> card.setEnabled(enabled));
    }

    /** Standard Swing button semantics with a card face and an explicit keyboard-focus outline. */
    private static final class CharacterCard extends JToggleButton {
        private final Image portrait;

        private CharacterCard(String character, Image portrait) {
            super(character);
            this.portrait = portrait;
            setPreferredSize(new Dimension(160, 216));
            setMinimumSize(new Dimension(112, 216));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setRolloverEnabled(true);
            setToolTipText("Select " + character);
            getAccessibleContext().setAccessibleName(character);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(isSelected() ? new Color(35, 57, 80)
                        : getModel().isRollover() && isEnabled() ? new Color(42, 52, 70) : new Color(28, 37, 51));
                g.fillRoundRect(2, 2, getWidth() - 4, getHeight() - 4, 16, 16);
                g.setColor(isSelected() ? ACCENT : new Color(66, 80, 100));
                g.setStroke(new BasicStroke(isSelected() ? 3f : 1f));
                g.drawRoundRect(2, 2, getWidth() - 5, getHeight() - 5, 16, 16);

                int size = Math.min(96, getWidth() - 24);
                int x = (getWidth() - size) / 2;
                int y = 26;
                g.setColor(BACKGROUND);
                g.fillRoundRect(x, y, size, size, 8, 8);
                if (portrait != null) {
                    double scale = Math.min((double) size / portrait.getWidth(null),
                            (double) size / portrait.getHeight(null));
                    int width = (int) Math.round(portrait.getWidth(null) * scale);
                    int height = (int) Math.round(portrait.getHeight(null) * scale);
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                    g.drawImage(portrait, x + (size - width) / 2, y + (size - height) / 2,
                            width, height, null);
                } else {
                    g.setFont(getFont().deriveFont(Font.BOLD, 32f));
                    g.setColor(Color.LIGHT_GRAY);
                    centered(g, "?", y + size / 2 + 12);
                }
                g.setFont(getFont().deriveFont(Font.BOLD, 13f));
                g.setColor(isEnabled() ? Color.WHITE : new Color(160, 173, 190));
                centered(g, getText(), 154);
                if (isSelected()) {
                    g.setFont(getFont().deriveFont(Font.PLAIN, 12f));
                    g.setColor(ACCENT);
                    centered(g, "Selected", 181);
                }
                if (hasFocus() && isEnabled()) {
                    g.setColor(Color.WHITE);
                    g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND,
                            0, new float[]{3, 3}, 0));
                    g.drawRoundRect(8, 8, getWidth() - 17, getHeight() - 17, 12, 12);
                }
            } finally {
                g.dispose();
            }
        }

        private void centered(Graphics2D g, String text, int baseline) {
            FontMetrics metrics = g.getFontMetrics();
            g.drawString(text, (getWidth() - metrics.stringWidth(text)) / 2, baseline);
        }
    }
}
