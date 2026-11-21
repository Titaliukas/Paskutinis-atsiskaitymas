package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import lt.ktu.paskutinisatsiskaitymas.protocol.LobbyList;
import lt.ktu.paskutinisatsiskaitymas.protocol.LobbySummary;

final class RoomBrowserPanel extends JPanel {
    private final RoomTable model = new RoomTable();
    private final JTable table = new JTable(model);
    private final JButton joinSelected = new JButton("Join selected");
    private final JButton joinByCode = new JButton("Join by code");
    private final JButton create = new JButton("Create room");
    private final JButton refresh = new JButton("Refresh");
    private final JButton previous = new JButton("Previous");
    private final JButton next = new JButton("Next");
    private final JTextField code = new JTextField(8);
    private final JLabel pageInfo = new JLabel();
    private final List<String> cursors = new ArrayList<>();
    private boolean hasMore;
    private boolean busy;

    RoomBrowserPanel(Runnable createRoom, Consumer<String> joinRoom, Consumer<String> loadPage) {
        super(new BorderLayout(10, 12));
        setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        JLabel title = new JLabel("Available rooms");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        add(title, BorderLayout.NORTH);

        DefaultTableCellRenderer plainText = new DefaultTableCellRenderer();
        plainText.putClientProperty("html.disable", Boolean.TRUE);
        table.setDefaultRenderer(Object.class, plainText);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.setRowHeight(26);
        table.getColumnModel().getColumn(0).setPreferredWidth(250);
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout());
        JPanel pages = new JPanel(new FlowLayout(FlowLayout.LEFT));
        pages.add(previous);
        pages.add(next);
        pages.add(refresh);
        pages.add(pageInfo);
        footer.add(pages, BorderLayout.NORTH);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(new JLabel("Room code"));
        actions.add(code);
        actions.add(joinByCode);
        actions.add(joinSelected);
        actions.add(create);
        footer.add(actions, BorderLayout.SOUTH);
        add(footer, BorderLayout.SOUTH);

        table.getSelectionModel().addListSelectionListener(event -> updateActions());
        joinSelected.addActionListener(event -> joinRoom.accept(model.rooms.get(table.getSelectedRow()).code()));
        joinByCode.addActionListener(event -> joinRoom.accept(code.getText()));
        code.addActionListener(event -> joinByCode.doClick());
        create.addActionListener(event -> createRoom.run());
        refresh.addActionListener(event -> {
            resetPages();
            loadPage.accept(null);
        });
        previous.addActionListener(event -> {
            cursors.removeLast();
            loadPage.accept(cursors.getLast());
        });
        next.addActionListener(event -> {
            String cursor = model.rooms.getLast().code();
            cursors.add(cursor);
            loadPage.accept(cursor);
        });
        resetPages();
    }

    void resetPages() {
        cursors.clear();
        cursors.add(null);
        hasMore = false;
        model.rooms = List.of();
        model.fireTableDataChanged();
        pageInfo.setText("Loading rooms...");
        updateActions();
    }

    void showPage(LobbyList page) {
        model.rooms = page.rooms();
        hasMore = page.hasMore();
        table.clearSelection();
        model.fireTableDataChanged();
        pageInfo.setText(page.rooms().isEmpty()
                ? cursors.size() == 1 ? "No open rooms" : "No more open rooms"
                : "Page " + cursors.size());
        updateActions();
    }

    void setBusy(boolean busy) {
        this.busy = busy;
        updateActions();
    }

    private void updateActions() {
        table.setEnabled(!busy);
        code.setEnabled(!busy);
        joinByCode.setEnabled(!busy);
        joinSelected.setEnabled(!busy && table.getSelectedRow() >= 0);
        create.setEnabled(!busy);
        refresh.setEnabled(!busy);
        previous.setEnabled(!busy && cursors.size() > 1);
        next.setEnabled(!busy && hasMore);
    }

    private static final class RoomTable extends AbstractTableModel {
        private static final String[] COLUMNS = {"Host", "Code", "Players"};
        private List<LobbySummary> rooms = List.of();

        @Override public int getRowCount() { return rooms.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int column) { return COLUMNS[column]; }

        @Override
        public Object getValueAt(int row, int column) {
            LobbySummary room = rooms.get(row);
            return switch (column) {
                case 0 -> room.hostNickname();
                case 1 -> room.code();
                case 2 -> room.playerCount() + "/4";
                default -> throw new IndexOutOfBoundsException(column);
            };
        }
    }
}
