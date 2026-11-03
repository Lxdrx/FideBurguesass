import java.awt.*;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

public class POSApp {
    private final PuntoVenta negocio = new PuntoVenta();
    private final JFrame ventana = new JFrame("FideBurguesas POS");
    private final NumberFormat moneda = NumberFormat.getCurrencyInstance(new Locale("es", "CR"));

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new POSApp().acceso());
    }

    private void acceso() {
        JTextField usuario = new JTextField(20);
        JPasswordField clave = new JPasswordField(20);
        JLabel aviso = new JLabel(" ");
        JButton ingresar = new JButton("Ingresar");
        JPanel panel = formulario("Usuario", usuario, "Contraseña", clave);
        panel.add(new JLabel());
        panel.add(ingresar);
        JPanel contenido = new JPanel(new BorderLayout(8, 8));
        contenido.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        contenido.add(new JLabel("FideBurguesas POS"), BorderLayout.NORTH);
        contenido.add(panel, BorderLayout.CENTER);
        contenido.add(aviso, BorderLayout.SOUTH);
        ingresar.addActionListener(e -> {
            try {
                principal(negocio.ingresar(usuario.getText(), new String(clave.getPassword())));
            } catch (OperacionInvalida ex) {
                aviso.setText(ex.getMessage());
            } finally {
                clave.setText("");
            }
        });
        ventana.getRootPane().setDefaultButton(ingresar);
        mostrar(contenido, 440, 230);
    }

    private void principal(Perfil sesion) {
        JTabbedPane pestanas = new JTabbedPane();
        pestanas.addTab("Pedidos / Caja", caja(sesion));
        pestanas.addTab("Monitor de cocina", new JLabel("Monitor previsto para el siguiente avance."));
        pestanas.addTab("Productos y combos", catalogo());
        pestanas.addTab("Cajeros", perfiles(sesion));
        pestanas.setEnabledAt(2, sesion.admin);
        pestanas.setEnabledAt(3, sesion.admin);

        JButton salir = new JButton("Cerrar sesión");
        salir.addActionListener(e -> acceso());
        JPanel encabezado = new JPanel(new BorderLayout());
        encabezado.add(new JLabel("FideBurguesas | Punto de venta | Sesión: "
                + sesion.usuario + " (" + sesion.rol() + ")"));
        encabezado.add(salir, BorderLayout.EAST);
        JPanel contenido = new JPanel(new BorderLayout(8, 8));
        contenido.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        contenido.add(encabezado, BorderLayout.NORTH);
        contenido.add(pestanas);
        ventana.getRootPane().setDefaultButton(null);
        mostrar(contenido, 980, 480);
    }

    private JPanel catalogo() {
        DefaultTableModel modelo = modelo("Nombre", "Tipo", "Precio", "Descripción");
        JTable tabla = new JTable(modelo);
        Runnable actualizar = () -> {
            modelo.setRowCount(0);
            for (Articulo a : negocio.catalogo) {
                modelo.addRow(new Object[]{a.nombre, a.tipo(), moneda.format(a.precio), a.descripcion});
            }
        };
        JButton producto = new JButton("Registrar producto");
        JButton combo = new JButton("Registrar combo");
        JButton eliminar = new JButton("Eliminar seleccionado");
        JButton refrescar = new JButton("Actualizar");
        producto.addActionListener(e -> registrarArticulo(false, actualizar));
        combo.addActionListener(e -> registrarArticulo(true, actualizar));
        eliminar.addActionListener(e -> {
            int fila = tabla.getSelectedRow();
            if (fila >= 0) {
                negocio.catalogo.remove(fila);
                actualizar.run();
            }
        });
        refrescar.addActionListener(e -> actualizar.run());
        actualizar.run();
        return tablaConBotones(tabla, producto, combo, eliminar, refrescar);
    }

    private void registrarArticulo(boolean combo, Runnable actualizar) {
        JTextField nombre = new JTextField(20);
        JTextField precio = new JTextField(20);
        JTextField descripcion = new JTextField(20);
        JPanel campos = formulario("Nombre", nombre, "Precio", precio, "Descripción", descripcion);
        while (confirmar(campos, combo ? "Registrar combo" : "Registrar producto")) {
            try {
                negocio.registrar(nombre.getText(), precio.getText(), descripcion.getText(), combo);
                actualizar.run();
                return;
            } catch (OperacionInvalida ex) {
                error(ex);
            }
        }
    }

    private JPanel perfiles(Perfil sesion) {
        DefaultTableModel modelo = modelo("Usuario", "Rol");
        JTable tabla = new JTable(modelo);
        Runnable actualizar = () -> {
            modelo.setRowCount(0);
            for (Perfil p : negocio.perfiles) {
                modelo.addRow(new Object[]{p.usuario, p.rol()});
            }
        };
        JButton crear = new JButton("Crear perfil de cajero");
        JButton eliminar = new JButton("Eliminar perfil seleccionado");
        crear.addActionListener(e -> {
            JTextField usuario = new JTextField(20);
            JPasswordField clave = new JPasswordField(20);
            JComboBox<String> rol = new JComboBox<>(new String[]{"Cajero", "Administrador"});
            JPanel campos = formulario("Usuario", usuario, "Contraseña", clave, "Rol", rol);
            while (confirmar(campos, "Crear perfil")) {
                try {
                    negocio.crearPerfil(usuario.getText(), new String(clave.getPassword()),
                            rol.getSelectedIndex() == 1);
                    actualizar.run();
                    return;
                } catch (OperacionInvalida ex) {
                    error(ex);
                }
            }
        });
        eliminar.addActionListener(e -> {
            int fila = tabla.getSelectedRow();
            if (fila < 0) {
                return;
            }
            try {
                negocio.eliminarPerfil(fila, sesion);
                actualizar.run();
            } catch (OperacionInvalida ex) {
                error(ex);
            }
        });
        actualizar.run();
        return tablaConBotones(tabla, crear, eliminar);
    }

    private JPanel caja(Perfil sesion) {
        JComboBox<Articulo> selector = new JComboBox<>();
        JSpinner cantidad = new JSpinner(new SpinnerNumberModel(1, 1, 999, 1));
        JTextField cliente = new JTextField(16);
        java.util.List<LineaPedido> carrito = new java.util.ArrayList<>();
        DefaultTableModel modelo = modelo("Producto", "Tipo", "Cantidad", "Precio unitario", "Subtotal");
        JTable tabla = new JTable(modelo);
        JLabel total = new JLabel();
        Runnable actualizar = () -> {
            modelo.setRowCount(0);
            BigDecimal suma = BigDecimal.ZERO;
            for (LineaPedido linea : carrito) {
                modelo.addRow(new Object[]{linea.articulo.nombre, linea.articulo.tipo(), linea.cantidad,
                    moneda.format(linea.articulo.precio), moneda.format(linea.subtotal())});
                suma = suma.add(linea.subtotal());
            }
            total.setText("Total: " + moneda.format(suma));
        };
        JButton agregar = new JButton("Agregar al pedido");
        JButton quitar = new JButton("Quitar seleccionado");
        JButton vaciar = new JButton("Vaciar pedido");
        JButton guardar = new JButton("Confirmar pedido y generar factura");
        agregar.addActionListener(e -> {
            Articulo articulo = (Articulo) selector.getSelectedItem();
            if (articulo != null) {
                carrito.add(new LineaPedido(articulo, (Integer) cantidad.getValue()));
                actualizar.run();
            }
        });
        quitar.addActionListener(e -> {
            if (tabla.getSelectedRow() >= 0) {
                carrito.remove(tabla.getSelectedRow());
                actualizar.run();
            }
        });
        vaciar.addActionListener(e -> {
            carrito.clear();
            actualizar.run();
        });
        guardar.addActionListener(e -> {
            try {
                String archivo = negocio.confirmarPedido(cliente.getText(), sesion, carrito, moneda);
                carrito.clear();
                cliente.setText("");
                actualizar.run();
                JOptionPane.showMessageDialog(ventana, "Factura guardada en " + archivo);
            } catch (OperacionInvalida ex) {
                error(ex);
            }
        });
        JPanel panel = tablaConBotones(tabla, new JLabel("Cliente"), cliente, quitar, vaciar, total, guardar);
        JPanel controles = new JPanel();
        controles.add(new JLabel("Producto o combo"));
        controles.add(selector);
        controles.add(new JLabel("Cantidad"));
        controles.add(cantidad);
        controles.add(agregar);
        panel.add(controles, BorderLayout.NORTH);
        panel.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0
                    && panel.isShowing()) {
                selector.setModel(new DefaultComboBoxModel<>(negocio.catalogo.toArray(new Articulo[0])));
            }
        });
        actualizar.run();
        return panel;
    }

    private static DefaultTableModel modelo(String... columnas) {
        return new DefaultTableModel(columnas, 0) {
            @Override public boolean isCellEditable(int fila, int columna) {
                return false;
            }
        };
    }

    private static JPanel formulario(Object... campos) {
        JPanel panel = new JPanel(new GridLayout(0, 2, 8, 8));
        for (int i = 0; i < campos.length; i += 2) {
            panel.add(new JLabel((String) campos[i]));
            panel.add((Component) campos[i + 1]);
        }
        return panel;
    }

    private static JPanel tablaConBotones(JTable tabla, Component... acciones) {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.add(new JScrollPane(tabla));
        JPanel botones = new JPanel();
        for (Component accion : acciones) {
            botones.add(accion);
        }
        panel.add(botones, BorderLayout.SOUTH);
        return panel;
    }

    private boolean confirmar(JPanel campos, String titulo) {
        return JOptionPane.showConfirmDialog(ventana, campos, titulo,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION;
    }

    private void error(OperacionInvalida ex) {
        JOptionPane.showMessageDialog(ventana, ex.getMessage(), "FideBurguesas",
                JOptionPane.WARNING_MESSAGE);
    }

    private void mostrar(JPanel contenido, int ancho, int alto) {
        ventana.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        ventana.setContentPane(contenido);
        ventana.setSize(ancho, alto);
        ventana.setLocationRelativeTo(null);
        ventana.setVisible(true);
    }
}
