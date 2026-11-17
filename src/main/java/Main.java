import analisis.AnalizadorInfluencia;
import analisis.AnalizadorRed;
import analisis.Comunidad;
import analisis.ResultadoInfluencia;
import datos.CargadorJson;
import modelo.Arco;
import modelo.Grafo;
import modelo.PerfilEstudiante;
import modelo.TipoRelacion;
import modelo.Usuario;
import web.ServidorWeb;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;

/**
 * Punto de entrada de Nexo, la red de amistades estudiantiles.
 * <p>
 * Arranca la interfaz gráfica en {@code http://localhost:8080} y, al mismo tiempo, un menú de
 * consola. Los dos trabajan sobre el mismo grafo.
 */
public class Main {
    private static final int PUERTO = 8080;
    private static final Scanner ENTRADA = new Scanner(System.in);
    private static final CargadorJson CARGADOR = new CargadorJson();
    private static ServidorWeb servidor;

    /**
     * @param args no se usan
     */
    public static void main(String[] args) {
        servidor = new ServidorWeb(new Grafo(), PUERTO);
        try {
            servidor.iniciar();
            System.out.println("Nexo está listo en http://localhost:" + PUERTO);
        } catch (IOException e) {
            System.out.println("No se pudo iniciar el servidor en el puerto " + PUERTO
                + " (" + e.getMessage() + "). Continúa solo la consola.");
        }

        boolean salir = false;
        try {
            while (!salir) {
                mostrarMenu();
                int opcion = leerEntero("Opción");
                try {
                    salir = ejecutar(opcion);
                } catch (IllegalArgumentException e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }
        } catch (NoSuchElementException e) {
            System.out.println("\nEntrada cerrada.");
        }

        servidor.detener();
        System.out.println("Programa finalizado.");
    }

    // Menú

    private static void mostrarMenu() {
        System.out.println();
        System.out.println("===== NEXO · RED DE ESTUDIANTES =====");
        System.out.println("-- Estudiantes y relaciones --");
        System.out.println(" 1. Agregar estudiante");
        System.out.println(" 2. Eliminar estudiante");
        System.out.println(" 3. Ver perfil de un estudiante");
        System.out.println(" 4. Crear relación");
        System.out.println(" 5. Cambiar tipo de relación");
        System.out.println(" 6. Cambiar afinidad (peso) de relación");
        System.out.println(" 7. Eliminar relación (par completo)");
        System.out.println(" 8. Ver grafo (lista de adyacencia)");
        System.out.println("-- Recorridos --");
        System.out.println(" 9. Recorrido BFS");
        System.out.println("10. Recorrido DFS");
        System.out.println("11. Camino más corto (BFS)");
        System.out.println("12. Camino de mayor afinidad (Dijkstra)");
        System.out.println("-- Análisis --");
        System.out.println("13. Amigos en común");
        System.out.println("14. Sugerir amigos");
        System.out.println("15. Detectar comunidades");
        System.out.println("16. Ranking de influencia");
        System.out.println("-- Datos --");
        System.out.println("17. Cargar datos desde JSON");
        System.out.println(" 0. Salir");
    }

    private static boolean ejecutar(int opcion) {
        switch (opcion) {
            case 1: agregarUsuario(); break;
            case 2: eliminarUsuario(); break;
            case 3: verPerfil(); break;
            case 4: crearRelacion(); break;
            case 5: cambiarTipoRelacion(); break;
            case 6: cambiarPeso(); break;
            case 7: eliminarRelacion(); break;
            case 8: System.out.print(grafo()); break;
            case 9: bfs(); break;
            case 10: dfs(); break;
            case 11: caminoMasCorto(); break;
            case 12: caminoMayorAfinidad(); break;
            case 13: amigosEnComun(); break;
            case 14: sugerirAmigos(); break;
            case 15: detectarComunidades(); break;
            case 16: rankingInfluencia(); break;
            case 17: cargarJson(); break;
            case 0: return true;
            default: System.out.println("Opción no válida.");
        }
        return false;
    }

    private static Grafo grafo() {
        return servidor.getGrafo();
    }

    // Estudiantes y relaciones

    private static void agregarUsuario() {
        String nombre = leerTexto("Nombre");
        System.out.println("Datos opcionales (Enter para omitir):");
        String carrera = leerTexto("  Carrera");
        int semestre = leerEnteroOpcional("  Semestre");
        int edad = leerEnteroOpcional("  Edad");
        String correo = leerTexto("  Correo");
        PerfilEstudiante perfil = new PerfilEstudiante(carrera, semestre, edad, correo);
        Usuario usuario = grafo().agregarUsuario(nombre, perfil);
        System.out.println("Estudiante agregado: " + usuario + " — " + perfil);
    }

    private static void eliminarUsuario() {
        int id = leerEntero("ID del estudiante");
        String descripcion = describir(id);
        grafo().eliminarUsuario(id);
        System.out.println("Estudiante eliminado: " + descripcion);
    }

    private static void verPerfil() {
        int id = leerEntero("ID del estudiante");
        Usuario usuario = grafo().getUsuario(id);
        if (usuario == null) {
            throw new IllegalArgumentException("No existe el usuario con ID " + id);
        }
        System.out.println(usuario + " — " + usuario.getPerfil());
        System.out.println("  Opina de otros (salientes):");
        imprimirArcos(usuario.getSalientes(), true);
        System.out.println("  Opinan de él (entrantes):");
        imprimirArcos(usuario.getEntrantes(), false);
    }

    private static void imprimirArcos(List<Arco> arcos, boolean salientes) {
        if (arcos.isEmpty()) {
            System.out.println("    (ninguno)");
            return;
        }
        for (Arco arco : arcos) {
            int otro = salientes ? arco.getDestino() : arco.getOrigen();
            System.out.println("    " + (salientes ? "→ " : "← ") + describir(otro)
                + ": " + arco.getTipo().getEtiqueta() + ", afinidad " + arco.getPeso());
        }
    }

    private static void crearRelacion() {
        int origen = leerEntero("ID origen");
        int destino = leerEntero("ID destino");
        TipoRelacion tipo = leerTipo();
        double peso = leerPesoOpcional(tipo.getPesoPorDefecto());
        grafo().agregarRelacion(origen, destino, tipo, peso);
        System.out.println("Relación creada: " + origen + " -> " + destino + " (" + tipo.getEtiqueta()
            + ", afinidad " + peso + ")");
        System.out.println("La relación " + destino + " -> " + origen + " quedó como Desconocido.");
    }

    private static void cambiarTipoRelacion() {
        int origen = leerEntero("ID origen");
        int destino = leerEntero("ID destino");
        TipoRelacion tipo = leerTipo();
        grafo().cambiarTipoRelacion(origen, destino, tipo);
        System.out.println("Relación " + origen + " -> " + destino + " ahora es " + tipo.getEtiqueta());
    }

    private static void cambiarPeso() {
        int origen = leerEntero("ID origen");
        int destino = leerEntero("ID destino");
        double peso = leerDecimal("Afinidad (mayor que 0, máximo " + Arco.PESO_MAXIMO + ")");
        grafo().cambiarPeso(origen, destino, peso);
        System.out.println("Relación " + origen + " -> " + destino + " ahora tiene afinidad " + peso);
    }

    private static void eliminarRelacion() {
        int a = leerEntero("ID del primer estudiante");
        int b = leerEntero("ID del segundo estudiante");
        grafo().eliminarRelacion(a, b);
        System.out.println("Relación eliminada entre " + a + " y " + b);
    }

    // Recorridos

    private static void bfs() {
        int inicio = leerEntero("ID de inicio");
        imprimirOrden("BFS", new AnalizadorRed(grafo()).bfs(inicio));
    }

    private static void dfs() {
        int inicio = leerEntero("ID de inicio");
        imprimirOrden("DFS", new AnalizadorRed(grafo()).dfs(inicio));
    }

    private static void imprimirOrden(String titulo, List<Integer> orden) {
        System.out.println(titulo + " (solo Amistad):");
        for (int i = 0; i < orden.size(); i++) {
            System.out.println("  " + (i + 1) + ". " + describir(orden.get(i)));
        }
    }

    private static void caminoMasCorto() {
        int origen = leerEntero("ID origen");
        int destino = leerEntero("ID destino");
        List<Integer> camino = new AnalizadorRed(grafo()).caminoMasCorto(origen, destino);
        if (camino.isEmpty()) {
            System.out.println("No hay camino de Amistad de " + describir(origen) + " hacia " + describir(destino));
            return;
        }
        System.out.println("Camino más corto (" + (camino.size() - 1) + " pasos): " + unir(camino));
    }

    private static void caminoMayorAfinidad() {
        int origen = leerEntero("ID origen");
        int destino = leerEntero("ID destino");
        AnalizadorRed analizador = new AnalizadorRed(grafo());
        List<Integer> camino = analizador.caminoMayorAfinidad(origen, destino);
        if (camino.isEmpty()) {
            System.out.println("No hay camino de Amistad de " + describir(origen) + " hacia " + describir(destino));
            return;
        }
        System.out.println("Camino de mayor afinidad (" + (camino.size() - 1) + " pasos, costo "
            + String.format("%.3f", analizador.costoCamino(camino)) + "): " + unir(camino));
    }

    private static String unir(List<Integer> camino) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < camino.size(); i++) {
            if (i > 0) sb.append(" -> ");
            sb.append(describir(camino.get(i)));
        }
        return sb.toString();
    }

    // Análisis

    private static void amigosEnComun() {
        int a = leerEntero("ID del primer estudiante");
        int b = leerEntero("ID del segundo estudiante");
        List<Integer> comunes = new AnalizadorRed(grafo()).amigosEnComun(a, b);
        if (comunes.isEmpty()) {
            System.out.println("No tienen amigos en común.");
            return;
        }
        System.out.println("Amigos en común de " + describir(a) + " y " + describir(b) + ":");
        for (int id : comunes) {
            System.out.println("  " + describir(id));
        }
    }

    private static void sugerirAmigos() {
        int id = leerEntero("ID del estudiante");
        Map<Integer, Integer> sugerencias = new AnalizadorRed(grafo()).sugerirAmigos(id);
        if (sugerencias.isEmpty()) {
            System.out.println("Sin sugerencias para " + describir(id));
            return;
        }
        System.out.println("Sugerencias para " + describir(id) + ":");
        for (Map.Entry<Integer, Integer> entrada : sugerencias.entrySet()) {
            System.out.println("  " + describir(entrada.getKey()) + " - " + entrada.getValue() + " en común");
        }
    }

    private static void detectarComunidades() {
        List<Comunidad> comunidades = new AnalizadorRed(grafo()).detectarComunidades();
        if (comunidades.isEmpty()) {
            System.out.println("No hay comunidades. Se necesitan al menos 3 estudiantes con amistad mutua entre sí.");
            return;
        }
        for (Comunidad comunidad : comunidades) {
            System.out.println("Comunidad " + comunidad.getId() + " (" + comunidad.getTamano() + " miembros) — "
                + comunidad.getFuerza().getEtiqueta().toUpperCase());
            for (int id : comunidad.getMiembros()) {
                System.out.println("  " + describir(id));
            }
            System.out.println(String.format("  Densidad: %d de %d parejas posibles = %.0f%%",
                comunidad.getConexiones(), comunidad.getConexionesPosibles(), comunidad.getDensidad() * 100));
            System.out.println("  Núcleo (clique máxima): " + nombres(comunidad.getNucleo()));
            System.out.println(String.format("  Afinidad promedio: %.2f", comunidad.getAfinidadPromedio()));
            StringBuilder sb = new StringBuilder();
            for (int[] t : comunidad.getTriangulos()) {
                if (sb.length() > 0) sb.append("; ");
                sb.append(t[0]).append("-").append(t[1]).append("-").append(t[2]);
            }
            System.out.println("  Triángulos: " + sb);
        }
    }

    private static String nombres(List<Integer> ids) {
        StringBuilder sb = new StringBuilder();
        for (int id : ids) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(describir(id));
        }
        return sb.toString();
    }

    private static void rankingInfluencia() {
        List<ResultadoInfluencia> ranking = new AnalizadorInfluencia(grafo()).rankingInfluencia();
        if (ranking.isEmpty()) {
            System.out.println("Sin estudiantes.");
            return;
        }
        System.out.println("Ranking de influencia (solo Amistad):");
        for (int i = 0; i < ranking.size(); i++) {
            System.out.println("  " + (i + 1) + ". " + ranking.get(i));
        }
    }

    // Datos

    private static void cargarJson() {
        String ruta = leerTexto("Ruta del archivo JSON");
        Grafo nuevo = CARGADOR.cargarDesdeArchivo(ruta);
        servidor.reemplazarGrafo(nuevo);
        System.out.println("Datos cargados: " + nuevo.getNumUsuarios() + " estudiantes, "
            + nuevo.getAristas().size() + " pares de relaciones.");
    }

    // Lectura de datos

    private static String describir(int id) {
        Usuario usuario = grafo().getUsuario(id);
        return usuario == null ? String.valueOf(id) : usuario.toString();
    }

    private static TipoRelacion leerTipo() {
        TipoRelacion[] tipos = TipoRelacion.values();
        System.out.println("Tipos de relación:");
        for (int i = 0; i < tipos.length; i++) {
            System.out.println("  " + (i + 1) + ". " + tipos[i].getEtiqueta());
        }
        int opcion = leerEntero("Tipo");
        if (opcion < 1 || opcion > tipos.length) {
            throw new IllegalArgumentException("Tipo no válido: " + opcion);
        }
        return tipos[opcion - 1];
    }

    private static double leerPesoOpcional(double porDefecto) {
        while (true) {
            String linea = leerTexto("Afinidad (Enter = " + porDefecto + ", máximo " + Arco.PESO_MAXIMO + ")");
            if (linea.isEmpty()) return porDefecto;
            try {
                return Double.parseDouble(linea.replace(',', '.'));
            } catch (NumberFormatException e) {
                System.out.println("Escribe un número.");
            }
        }
    }

    private static double leerDecimal(String mensaje) {
        while (true) {
            String linea = leerTexto(mensaje);
            try {
                return Double.parseDouble(linea.replace(',', '.'));
            } catch (NumberFormatException e) {
                System.out.println("Escribe un número.");
            }
        }
    }

    private static int leerEntero(String mensaje) {
        while (true) {
            System.out.print(mensaje + ": ");
            String linea = ENTRADA.nextLine().trim();
            try {
                return Integer.parseInt(linea);
            } catch (NumberFormatException e) {
                System.out.println("Escribe un número entero.");
            }
        }
    }

    private static int leerEnteroOpcional(String mensaje) {
        while (true) {
            String linea = leerTexto(mensaje);
            if (linea.isEmpty()) return 0;
            try {
                return Integer.parseInt(linea);
            } catch (NumberFormatException e) {
                System.out.println("Escribe un número entero o deja vacío.");
            }
        }
    }

    private static String leerTexto(String mensaje) {
        System.out.print(mensaje + ": ");
        return ENTRADA.nextLine().trim();
    }
}
