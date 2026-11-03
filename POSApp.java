import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class POSApp {
    private static final String DATA_FILE = "fideburguesas-data.dat";
    private static final String INVOICE_DIR = "facturas";
    private final java.util.List<Product> products = new ArrayList<>();
    private final java.util.List<Cashier> cashiers = new ArrayList<>();
    private final java.util.List<Order> orders = new ArrayList<>();
    private final java.util.List<OrderItem> cart = new ArrayList<>();
    private JFrame frame;
    private String currentUser;
    private boolean admin;
    private DefaultTableModel productModel;
    private DefaultTableModel cashierModel;
    private DefaultTableModel kitchenModel;
    private DefaultTableModel cartModel;
    private JComboBox<Product> productSelector;
    private JSpinner quantitySpinner;
    private JTextField customerField;
    private JTable kitchenTable;
    private JLabel userLabel;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new POSApp().showLogin());
    }

    private POSApp() {
        loadData();
        if (cashiers.isEmpty()) {
            cashiers.add(new Cashier("admin", "admin123", "Administrador"));
            cashiers.add(new Cashier("caja", "caja123", "Cajero"));
            saveData();
        }
        if (products.isEmpty()) {
            products.add(new Product("Hamburguesa clásica", "Producto", 2500, "Hamburguesa de carne, lechuga y tomate"));
            products.add(new Product("Papas medianas", "Producto", 1000, "Papas fritas medianas"));
            products.add(new Product("Refresco", "Producto", 900, "Refresco de 500 ml"));
            products.add(new Product("Combo clásico", "Combo", 3900, "Hamburguesa clásica + papas medianas + refresco"));
            saveData();
        }
    }

    private void showLogin() {
        JFrame login = new JFrame("FideBurguesas POS - Inicio de sesión");
        login.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        login.setSize(430, 300);
        login.setLocationRelativeTo(null);
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(20, 28, 20, 28));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(7, 6, 7, 6);
        c.fill = GridBagConstraints.HORIZONTAL;
        JLabel title = new JLabel("FideBurguesas POS", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 23));
        c.gridx = 0; c.gridy = 0; c.gridwidth = 2; panel.add(title, c);
        c.gridwidth = 1;
        c.gridx = 0; c.gridy = 1; panel.add(new JLabel("Usuario:"), c);
        JTextField username = new JTextField(18);
        c.gridx = 1; panel.add(username, c);
        c.gridx = 0; c.gridy = 2; panel.add(new JLabel("Contraseña:"), c);
        JPasswordField password = new JPasswordField(18);
        c.gridx = 1; panel.add(password, c);
        JButton loginButton = new JButton("Ingresar");
        c.gridx = 0; c.gridy = 3; c.gridwidth = 2; panel.add(loginButton, c);
        JLabel help = new JLabel("Demo: admin / admin123   |   caja / caja123", SwingConstants.CENTER);
        help.setFont(new Font("SansSerif", Font.PLAIN, 11));
        c.gridy = 4; panel.add(help, c);
        loginButton.addActionListener(e -> {
            String name = username.getText().trim();
            String pass = new String(password.getPassword());
            Cashier found = cashiers.stream().filter(x -> x.username.equals(name) && x.password.equals(pass)).findFirst().orElse(null);
            if (found == null) {
                JOptionPane.showMessageDialog(login, "Usuario o contraseña incorrectos.", "Acceso denegado", JOptionPane.ERROR_MESSAGE);
                return;
            }
            currentUser = found.username;
            admin = found.role.equalsIgnoreCase("Administrador");
            login.dispose();
            showMainWindow();
        });
        password.addActionListener(e -> loginButton.doClick());
        login.setContentPane(panel);
        login.setVisible(true);
    }

    private void showMainWindow() {
        frame = new JFrame("FideBurguesas POS");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1100, 700);
        frame.setLocationRelativeTo(null);
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));
        JPanel top = new JPanel(new BorderLayout());
        JLabel heading = new JLabel("FideBurguesas | Punto de Venta");
        heading.setFont(new Font("SansSerif", Font.BOLD, 22));
        top.add(heading, BorderLayout.WEST);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        userLabel = new JLabel("Sesión: " + currentUser + (admin ? " (Administrador)" : " (Cajero)"));
        JButton logout = new JButton("Cerrar sesión");
        logout.addActionListener(e -> { frame.dispose(); showLogin(); });
        right.add(userLabel); right.add(logout); top.add(right, BorderLayout.EAST);
        root.add(top, BorderLayout.NORTH);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Pedidos / Caja", buildOrdersPanel());
        tabs.addTab("Monitor de cocina", buildKitchenPanel());
        tabs.addTab("Productos y combos", buildProductsPanel());
        tabs.addTab("Cajeros", buildCashiersPanel());
        if (!admin) {
            tabs.setEnabledAt(2, false);
            tabs.setEnabledAt(3, false);
        }
        root.add(tabs, BorderLayout.CENTER);
        frame.setContentPane(root);
        refreshAll();
        frame.setVisible(true);
    }

    private JPanel buildOrdersPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JPanel addPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        productSelector = new JComboBox<>();
        productSelector.setPreferredSize(new Dimension(280, 28));
        quantitySpinner = new JSpinner(new SpinnerNumberModel(1, 1, 100, 1));
        JButton add = new JButton("Agregar al pedido");
        addPanel.add(new JLabel("Producto o combo:")); addPanel.add(productSelector);
        addPanel.add(new JLabel("Cantidad:")); addPanel.add(quantitySpinner); addPanel.add(add);
        panel.add(addPanel, BorderLayout.NORTH);
        cartModel = new DefaultTableModel(new Object[]{"Producto", "Tipo", "Cantidad", "Precio unitario", "Subtotal"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable cartTable = new JTable(cartModel);
        panel.add(new JScrollPane(cartTable), BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(8, 8));
        JPanel customer = new JPanel(new FlowLayout(FlowLayout.LEFT));
        customer.add(new JLabel("Cliente:")); customerField = new JTextField(25); customer.add(customerField);
        JButton remove = new JButton("Quitar seleccionado");
        customer.add(remove);
        bottom.add(customer, BorderLayout.NORTH);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JLabel totalLabel = new JLabel("Total: ₡0"); totalLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        JButton clear = new JButton("Vaciar pedido");
        JButton checkout = new JButton("Confirmar pedido y generar factura");
        actions.add(totalLabel); actions.add(clear); actions.add(checkout); bottom.add(actions, BorderLayout.SOUTH);
        panel.add(bottom, BorderLayout.SOUTH);
        add.addActionListener(e -> {
            Product p = (Product) productSelector.getSelectedItem();
            if (p == null) return;
            int qty = (Integer) quantitySpinner.getValue();
            cart.add(new OrderItem(p.name, p.type, qty, p.price, p.description));
            refreshCart(totalLabel);
        });
        remove.addActionListener(e -> {
            int row = cartTable.getSelectedRow();
            if (row >= 0 && row < cart.size()) { cart.remove(row); refreshCart(totalLabel); }
        });
        clear.addActionListener(e -> { cart.clear(); refreshCart(totalLabel); });
        checkout.addActionListener(e -> {
            if (cart.isEmpty()) { JOptionPane.showMessageDialog(frame, "Agregue al menos un producto al pedido."); return; }
            String customerName = customerField.getText().trim();
            if (customerName.isEmpty()) { JOptionPane.showMessageDialog(frame, "Ingrese el nombre del cliente."); return; }
            Order order = new Order(orders.size() + 1, customerName, currentUser, new ArrayList<>(cart));
            orders.add(order);
            saveData();
            File invoice = createInvoice(order);
            cart.clear(); customerField.setText(""); refreshCart(totalLabel); refreshAll();
            String msg = "Pedido #" + order.id + " registrado.\n" + (invoice == null ? "No se pudo crear la factura." : "Factura creada: " + invoice.getPath());
            JOptionPane.showMessageDialog(frame, msg, "Pedido confirmado", JOptionPane.INFORMATION_MESSAGE);
        });
        return panel;
    }

    private void refreshCart(JLabel totalLabel) {
        cartModel.setRowCount(0);
        double total = 0;
        for (OrderItem item : cart) {
            double subtotal = item.quantity * item.price; total += subtotal;
            cartModel.addRow(new Object[]{item.name, item.type, item.quantity, money(item.price), money(subtotal)});
        }
        totalLabel.setText("Total: " + money(total));
    }

    private JPanel buildKitchenPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        kitchenModel = new DefaultTableModel(new Object[]{"Pedido", "Cliente", "Detalle", "Hora", "Estado", "Cajero"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        kitchenTable = new JTable(kitchenModel);
        kitchenTable.setRowHeight(25);
        panel.add(new JScrollPane(kitchenTable), BorderLayout.CENTER);
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton preparing = new JButton("Marcar en preparación");
        JButton ready = new JButton("Marcar listo");
        JButton delivered = new JButton("Marcar entregado");
        JButton refresh = new JButton("Actualizar monitor");
        controls.add(preparing); controls.add(ready); controls.add(delivered); controls.add(refresh);
        panel.add(controls, BorderLayout.SOUTH);
        preparing.addActionListener(e -> setSelectedStatus("En preparación"));
        ready.addActionListener(e -> setSelectedStatus("Listo"));
        delivered.addActionListener(e -> setSelectedStatus("Entregado"));
        refresh.addActionListener(e -> refreshKitchen());
        return panel;
    }

    private void setSelectedStatus(String status) {
        int row = kitchenTable.getSelectedRow();
        if (row < 0 || row >= orders.size()) { JOptionPane.showMessageDialog(frame, "Seleccione un pedido."); return; }
        Order order = orders.get(row);
        order.status = status;
        saveData(); refreshKitchen();
    }

    private JPanel buildProductsPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        productModel = new DefaultTableModel(new Object[]{"Nombre", "Tipo", "Precio", "Descripción"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(productModel);
        table.setRowHeight(24);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addProduct = new JButton("Registrar producto");
        JButton addCombo = new JButton("Registrar combo");
        JButton delete = new JButton("Eliminar seleccionado");
        JButton refresh = new JButton("Actualizar");
        buttons.add(addProduct); buttons.add(addCombo); buttons.add(delete); buttons.add(refresh);
        panel.add(buttons, BorderLayout.SOUTH);
        addProduct.addActionListener(e -> showProductDialog("Producto"));
        addCombo.addActionListener(e -> showProductDialog("Combo"));
        delete.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0 && row < products.size()) {
                int answer = JOptionPane.showConfirmDialog(frame, "¿Eliminar " + products.get(row).name + "?", "Confirmar", JOptionPane.YES_NO_OPTION);
                if (answer == JOptionPane.YES_OPTION) { products.remove(row); saveData(); refreshAll(); }
            } else JOptionPane.showMessageDialog(frame, "Seleccione un producto.");
        });
        refresh.addActionListener(e -> refreshAll());
        return panel;
    }

    private void showProductDialog(String type) {
        JTextField name = new JTextField();
        JTextField price = new JTextField();
        JTextField description = new JTextField();
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Nombre:")); form.add(name);
        form.add(new JLabel("Precio (colones):")); form.add(price);
        form.add(new JLabel(type.equals("Combo") ? "Productos incluidos:" : "Descripción:")); form.add(description);
        int result = JOptionPane.showConfirmDialog(frame, form, "Registrar " + type.toLowerCase(), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;
        try {
            String n = name.getText().trim();
            double p = Double.parseDouble(price.getText().trim());
            String d = description.getText().trim();
            if (n.isEmpty() || d.isEmpty() || p <= 0) throw new IllegalArgumentException();
            products.add(new Product(n, type, p, d)); saveData(); refreshAll();
        } catch (Exception ex) { JOptionPane.showMessageDialog(frame, "Ingrese nombre, descripción y un precio válido mayor que cero.", "Datos inválidos", JOptionPane.ERROR_MESSAGE); }
    }

    private JPanel buildCashiersPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        cashierModel = new DefaultTableModel(new Object[]{"Usuario", "Rol"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(cashierModel);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton add = new JButton("Crear perfil de cajero");
        JButton delete = new JButton("Eliminar perfil seleccionado");
        buttons.add(add); buttons.add(delete); panel.add(buttons, BorderLayout.SOUTH);
        add.addActionListener(e -> showCashierDialog());
        delete.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0 || row >= cashiers.size()) { JOptionPane.showMessageDialog(frame, "Seleccione un perfil."); return; }
            Cashier selected = cashiers.get(row);
            if (selected.username.equals(currentUser)) { JOptionPane.showMessageDialog(frame, "No puede eliminar el usuario de la sesión actual."); return; }
            if (JOptionPane.showConfirmDialog(frame, "¿Eliminar el usuario " + selected.username + "?", "Confirmar", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                cashiers.remove(row); saveData(); refreshAll();
            }
        });
        return panel;
    }

    private void showCashierDialog() {
        JTextField username = new JTextField();
        JPasswordField password = new JPasswordField();
        JComboBox<String> role = new JComboBox<>(new String[]{"Cajero", "Administrador"});
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Usuario:")); form.add(username);
        form.add(new JLabel("Contraseña:")); form.add(password);
        form.add(new JLabel("Rol:")); form.add(role);
        if (JOptionPane.showConfirmDialog(frame, form, "Crear perfil", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        String u = username.getText().trim(); String p = new String(password.getPassword());
        if (u.isEmpty() || p.isEmpty() || cashiers.stream().anyMatch(x -> x.username.equalsIgnoreCase(u))) {
            JOptionPane.showMessageDialog(frame, "Complete los datos y use un nombre de usuario que no exista.", "Datos inválidos", JOptionPane.ERROR_MESSAGE); return;
        }
        cashiers.add(new Cashier(u, p, (String) role.getSelectedItem())); saveData(); refreshAll();
    }

    private void refreshAll() {
        if (productModel != null) {
            productModel.setRowCount(0);
            for (Product p : products) productModel.addRow(new Object[]{p.name, p.type, money(p.price), p.description});
        }
        if (cashierModel != null) {
            cashierModel.setRowCount(0);
            for (Cashier c : cashiers) cashierModel.addRow(new Object[]{c.username, c.role});
        }
        if (productSelector != null) {
            productSelector.removeAllItems();
            for (Product p : products) productSelector.addItem(p);
        }
        refreshKitchen();
    }

    private void refreshKitchen() {
        if (kitchenModel == null) return;
        kitchenModel.setRowCount(0);
        for (Order o : orders) {
            StringJoiner details = new StringJoiner(", ");
            for (OrderItem item : o.items) details.add(item.quantity + " x " + item.name);
            kitchenModel.addRow(new Object[]{o.id, o.customer, details.toString(), o.createdAt, o.status, o.cashier});
        }
    }

    private File createInvoice(Order order) {
        try {
            File dir = new File(INVOICE_DIR);
            if (!dir.exists() && !dir.mkdirs()) return null;
            File file = new File(dir, String.format("Factura_%05d.txt", order.id));
            try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), java.nio.charset.StandardCharsets.UTF_8))) {
                out.println("FideBurguesas");
                out.println("FACTURA DE VENTA");
                out.println("========================================");
                out.println("Factura: " + order.id);
                out.println("Fecha: " + order.createdAt);
                out.println("Cliente: " + order.customer);
                out.println("Cajero: " + order.cashier);
                out.println("----------------------------------------");
                out.printf("%-22s %5s %10s %10s%n", "Producto", "Cant.", "Precio", "Subtotal");
                double total = 0;
                for (OrderItem item : order.items) {
                    double subtotal = item.quantity * item.price; total += subtotal;
                    out.printf("%-22s %5d %10s %10s%n", shorten(item.name, 22), item.quantity, money(item.price), money(subtotal));
                }
                out.println("----------------------------------------");
                out.println("TOTAL: " + money(total));
                out.println("Estado del pedido: " + order.status);
                out.println("Gracias por su compra.");
            }
            return file;
        } catch (IOException ex) { return null; }
    }

    private void saveData() {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(DATA_FILE))) {
            out.writeObject(products); out.writeObject(cashiers); out.writeObject(orders);
        } catch (IOException ex) {
            if (frame != null) JOptionPane.showMessageDialog(frame, "No se pudieron guardar los datos: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    @SuppressWarnings("unchecked")
    private void loadData() {
        File file = new File(DATA_FILE);
        if (!file.exists()) return;
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
            products.addAll((java.util.List<Product>) in.readObject());
            cashiers.addAll((java.util.List<Cashier>) in.readObject());
            orders.addAll((java.util.List<Order>) in.readObject());
        } catch (IOException | ClassNotFoundException | ClassCastException ex) {
            products.clear(); cashiers.clear(); orders.clear();
            JOptionPane.showMessageDialog(null, "No se pudieron cargar los datos guardados. Se iniciará una sesión nueva.");
        }
    }

    private String money(double value) { return String.format(java.util.Locale.US, "₡%,.2f", value); }
    private String shorten(String value, int max) { return value.length() <= max ? value : value.substring(0, max - 3) + "..."; }

    private static class Product implements Serializable {
        private static final long serialVersionUID = 1L;
        String name, type, description;
        double price;
        Product(String name, String type, double price, String description) { this.name = name; this.type = type; this.price = price; this.description = description; }
        public String toString() { return name + " — " + moneyText(price) + " (" + type + ")"; }
        private static String moneyText(double n) { return String.format(java.util.Locale.US, "₡%,.2f", n); }
    }

    private static class Cashier implements Serializable {
        private static final long serialVersionUID = 1L;
        String username, password, role;
        Cashier(String username, String password, String role) { this.username = username; this.password = password; this.role = role; }
    }

    private static class OrderItem implements Serializable {
        private static final long serialVersionUID = 1L;
        String name, type, description;
        int quantity;
        double price;
        OrderItem(String name, String type, int quantity, double price, String description) { this.name = name; this.type = type; this.quantity = quantity; this.price = price; this.description = description; }
    }

    private static class Order implements Serializable {
        private static final long serialVersionUID = 1L;
        int id;
        String customer, cashier, createdAt, status;
        java.util.List<OrderItem> items;
        Order(int id, String customer, String cashier, java.util.List<OrderItem> items) {
            this.id = id; this.customer = customer; this.cashier = cashier; this.items = items;
            this.createdAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            this.status = "Pendiente";
        }
    }
}
