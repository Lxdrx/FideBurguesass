import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class PuntoVenta {
    final List<Perfil> perfiles = new ArrayList<>();
    final List<Articulo> catalogo = new ArrayList<>();
    final List<Pedido> pedidos = new ArrayList<>();

    PuntoVenta() {
        perfiles.add(new Perfil("admin", "admin123", true));
        perfiles.add(new Perfil("caja", "caja123", false));
        catalogo.add(new Producto("Hamburguesa clásica", new BigDecimal("2500"), "Carne y vegetales"));
        catalogo.add(new Combo("Combo clásico", new BigDecimal("3900"), "Hamburguesa, papas y bebida"));
    }

    Perfil ingresar(String usuario, String clave) throws OperacionInvalida {
        for (Perfil perfil : perfiles) {
            if (perfil.usuario.equalsIgnoreCase(usuario.trim()) && perfil.clave.equals(clave)) {
                return perfil;
            }
        }
        throw new OperacionInvalida("Usuario o contraseña incorrectos.");
    }

    void crearPerfil(String usuario, String clave, boolean admin) throws OperacionInvalida {
        usuario = requerido(usuario, "Indique el usuario.");
        requerido(clave, "Indique la contraseña.");
        for (Perfil perfil : perfiles) {
            if (perfil.usuario.equalsIgnoreCase(usuario)) {
                throw new OperacionInvalida("El usuario ya está registrado.");
            }
        }
        perfiles.add(new Perfil(usuario, clave, admin));
    }

    void eliminarPerfil(int fila, Perfil sesion) throws OperacionInvalida {
        if (perfiles.get(fila) == sesion) {
            throw new OperacionInvalida("El perfil con sesión activa no se puede eliminar.");
        }
        perfiles.remove(fila);
    }

    void registrar(String nombre, String precio, String descripcion, boolean combo)
            throws OperacionInvalida {
        nombre = requerido(nombre, "Indique el nombre.");
        descripcion = requerido(descripcion, "Indique la descripción.");
        BigDecimal monto;
        try {
            monto = new BigDecimal(precio.trim().replace(',', '.'));
        } catch (NumberFormatException ex) {
            throw new OperacionInvalida("Ingrese un precio numérico.");
        }
        if (monto.signum() <= 0 || monto.stripTrailingZeros().scale() > 2) {
            throw new OperacionInvalida("Ingrese un precio mayor que cero, con hasta dos decimales.");
        }
        catalogo.add(combo ? new Combo(nombre, monto, descripcion)
                           : new Producto(nombre, monto, descripcion));
    }

    String confirmarPedido(String cliente, Perfil sesion, List<LineaPedido> carrito, NumberFormat moneda)
            throws OperacionInvalida {
        cliente = requerido(cliente, "Indique el nombre del cliente.");
        if (carrito.isEmpty()) {
            throw new OperacionInvalida("Agregue al menos un artículo.");
        }
        Pedido pedido = new Pedido(cliente, sesion.usuario, carrito);
        Path factura = Paths.get("facturas", "Factura-" + pedido.numero + ".txt");
        StringBuilder detalle = new StringBuilder("FideBurguesas | Factura ").append(pedido.numero)
                .append("\nFecha: ").append(pedido.fecha.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")))
                .append("\nCliente: ").append(pedido.cliente)
                .append("\nCajero: ").append(pedido.cajero).append("\n\n");
        BigDecimal total = BigDecimal.ZERO;
        for (LineaPedido linea : pedido.lineas) {
            detalle.append(linea.cantidad).append(" x ").append(linea.articulo.nombre)
                    .append(" (").append(linea.articulo.tipo()).append(") | ")
                    .append(moneda.format(linea.articulo.precio)).append(" | Subtotal: ")
                    .append(moneda.format(linea.subtotal())).append('\n');
            total = total.add(linea.subtotal());
        }
        detalle.append("\nTotal: ").append(moneda.format(total)).append('\n');
        try {
            Files.createDirectories(factura.getParent());
            Files.write(factura, detalle.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new OperacionInvalida("No se pudo guardar la factura.");
        }
        pedidos.add(pedido);
        return factura.toString();
    }

    private static String requerido(String valor, String mensaje) throws OperacionInvalida {
        String texto = valor.trim();
        if (texto.isEmpty()) {
            throw new OperacionInvalida(mensaje);
        }
        return texto;
    }
}

final class OperacionInvalida extends Exception {
    OperacionInvalida(String mensaje) {
        super(mensaje);
    }
}

final class Perfil {
    final String usuario;
    final String clave;
    final boolean admin;

    Perfil(String usuario, String clave, boolean admin) {
        this.usuario = usuario;
        this.clave = clave;
        this.admin = admin;
    }

    String rol() {
        return admin ? "Administrador" : "Cajero";
    }
}

abstract class Articulo {
    final String nombre;
    final BigDecimal precio;
    final String descripcion;

    Articulo(String nombre, BigDecimal precio, String descripcion) {
        this.nombre = nombre;
        this.precio = precio;
        this.descripcion = descripcion;
    }

    abstract String tipo();

    @Override public String toString() {
        return nombre + " (" + tipo() + ")";
    }
}

final class Producto extends Articulo {
    Producto(String nombre, BigDecimal precio, String descripcion) {
        super(nombre, precio, descripcion);
    }

    @Override String tipo() {
        return "Producto";
    }
}

final class Combo extends Articulo {
    Combo(String nombre, BigDecimal precio, String descripcion) {
        super(nombre, precio, descripcion);
    }

    @Override String tipo() {
        return "Combo";
    }
}

final class LineaPedido {
    final Articulo articulo;
    final int cantidad;

    LineaPedido(Articulo articulo, int cantidad) {
        this.articulo = articulo;
        this.cantidad = cantidad;
    }

    BigDecimal subtotal() {
        return articulo.precio.multiply(BigDecimal.valueOf(cantidad));
    }
}

final class Pedido {
    final String numero = UUID.randomUUID().toString();
    final LocalDateTime fecha = LocalDateTime.now();
    final String cliente;
    final String cajero;
    final List<LineaPedido> lineas;

    Pedido(String cliente, String cajero, List<LineaPedido> lineas) {
        this.cliente = cliente;
        this.cajero = cajero;
        this.lineas = new ArrayList<>(lineas);
    }
}
