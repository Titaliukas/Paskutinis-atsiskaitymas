package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.util.UUID;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import lt.ktu.paskutinisatsiskaitymas.protocol.LobbyMember;
import lt.ktu.paskutinisatsiskaitymas.protocol.LobbyState;

/** Waiting-room view; all actions go through the facade to the server mediator. EDT only. */
final class LobbyPanel extends JPanel {
    private final JPanel roster = new JPanel(new GridLayout(1, 4, 12, 0));
    private final JButton start = new JButton("Start game");
    private final JLabel readiness = new JLabel();
    private boolean canStart;
    private boolean busy;

    LobbyPanel(Runnable startGame) {
        super(new BorderLayout(12, 16));
        setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        roster.setOpaque(false);
        JPanel players = new JPanel(new GridBagLayout());
        players.setBackground(new Color(17, 23, 34));
        players.setBorder(BorderFactory.createEmptyBorder(20, 16, 20, 16));
        GridBagConstraints row = new GridBagConstraints();
        row.weightx = 1;
        row.fill = GridBagConstraints.HORIZONTAL;
        players.add(roster, row);
        add(players, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(readiness);
        actions.add(start);
        add(actions, BorderLayout.SOUTH);
        start.setEnabled(false);
        start.addActionListener(event -> startGame.run());
    }

    void showLobby(LobbyState state, UUID localPlayerId) {
        roster.removeAll();
        for (int slot = 0; slot < 4; slot++) {
            int playerSlot = slot;
            LobbyMember member = state.members().stream().filter(value -> value.slot() == playerSlot)
                    .findFirst().orElse(null);
            roster.add(playerCard(member, state.hostId(), localPlayerId));
        }
        roster.revalidate();
        roster.repaint();
        boolean host = state.hostId().equals(localPlayerId);
        boolean allConfirmed = state.members().stream().allMatch(member -> member.character() != null);
        start.setVisible(host);
        canStart = host && allConfirmed && state.phase() == LobbyState.Phase.WAITING;
        readiness.setText(allConfirmed ? "Waiting for the host to start." : "Waiting for all players to choose.");
        start.setEnabled(canStart && !busy);
    }

    void setBusy(boolean busy) {
        this.busy = busy;
        start.setEnabled(canStart && !busy);
    }

    private JPanel playerCard(LobbyMember member, UUID hostId, UUID localPlayerId) {
        boolean local = member != null && member.playerId().equals(localPlayerId);
        JPanel card = new JPanel(new BorderLayout(0, 12));
        card.setBackground(new Color(28, 37, 51));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(local ? new Color(109, 186, 255) : new Color(66, 80, 100), 2),
                BorderFactory.createEmptyBorder(16, 8, 16, 8)));
        card.setPreferredSize(new Dimension(160, 220));

        JLabel portrait = new JLabel("?", SwingConstants.CENTER);
        portrait.setForeground(Color.LIGHT_GRAY);
        portrait.setFont(portrait.getFont().deriveFont(Font.BOLD, 48f));
        portrait.setPreferredSize(new Dimension(112, 112));
        if (member != null && member.character() != null) {
            Image image = AssetManager.getInstance().getImage(member.character() + "_PORTRAIT");
            if (image != null) {
                portrait.setText(null);
                portrait.setIcon(new ImageIcon(image.getScaledInstance(112, 112, Image.SCALE_FAST)));
            }
            portrait.getAccessibleContext().setAccessibleName(member.character() + " portrait");
        } else {
            portrait.getAccessibleContext().setAccessibleName(member == null ? "Empty player slot" : "Choosing character");
        }
        card.add(portrait, BorderLayout.NORTH);

        JLabel nickname = new JLabel(member == null ? "Waiting for player" : member.nickname(), SwingConstants.CENTER);
        nickname.putClientProperty("html.disable", Boolean.TRUE);
        nickname.setForeground(member == null ? Color.LIGHT_GRAY : Color.WHITE);
        nickname.setFont(nickname.getFont().deriveFont(Font.BOLD, 15f));
        if (member != null) {
            nickname.setToolTipText("Player: " + member.nickname());
        }
        card.add(nickname, BorderLayout.CENTER);

        boolean host = member != null && member.playerId().equals(hostId);
        String roleText = host ? local ? "Host \u00B7 You" : "Host" : local ? "You" : "";
        if (member != null && member.character() == null) {
            roleText = roleText.isEmpty() ? "Choosing character" : roleText + " \u00B7 Choosing character";
        }
        JLabel role = new JLabel(roleText.isEmpty() ? " " : roleText, SwingConstants.CENTER);
        role.setForeground(new Color(109, 186, 255));
        card.add(role, BorderLayout.SOUTH);
        return card;
    }
}
