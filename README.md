# FideBurguesas POS
## Avance 2 del proyecto final

### Ejecución

Abra la carpeta FideBurguesas como proyecto Maven en NetBeans y ejecute
`POSApp.java`. El código utiliza Java 8 o superior y Swing.

También se puede compilar y ejecutar con un JDK desde la carpeta del proyecto.

```text
javac -encoding UTF-8 -d salida src/main/java/POSApp.java src/main/java/PuntoVenta.java
java -cp salida POSApp
```

| Perfil de demostración | Usuario | Contraseña |
|---|---|---|
| Administrador | admin | admin123 |
| Cajero | caja | caja123 |

### Historias implementadas

| Historia | Función |
|---|---|
| HU-01 | Inicio de sesión, validación de credenciales y acceso por rol |
| HU-02 | Creación y eliminación de perfiles, con protección de la sesión activa |
| HU-03 | Registro de productos y combos, con validación de datos y precios |
| HU-04 | Carrito, cantidades, subtotales, total y confirmación del pedido |
| HU-05 | Factura de texto con número, fecha, cliente, cajero y desglose |

Se implementaron cinco de las siete historias del diseño. Los datos de la
sesión se mantienen en memoria. Las facturas se guardan en la carpeta
`facturas`. El monitor de cocina y la persistencia de registros corresponden
a HU-06 y HU-07, pendientes de implementación.

### Estructura

`POSApp` contiene la interfaz gráfica. `PuntoVenta` valida las operaciones y
mantiene las colecciones de perfiles, artículos y pedidos. `Producto` y `Combo`
heredan de `Articulo` y cada clase implementa `tipo()`, que se utiliza en el
catálogo, el carrito y la factura. `OperacionInvalida` comunica los errores de
validación a la interfaz.
