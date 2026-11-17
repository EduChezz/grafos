package modelo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Grafo dirigido de la red social, implementado con <b>listas de adyacencia</b>.
 * <p>
 * <b>Por qué listas y no matriz:</b> la cantidad de estudiantes no se conoce de antemano y la red
 * es dispersa (cada estudiante se relaciona con pocos). Con memoria dinámica solo se guardan las
 * relaciones que existen. Una matriz booleana de n×n sería más rápida para preguntar "¿A conoce a B?",
 * pero exige conocer el máximo de vértices y reservar n² celdas aunque casi todas estén vacías.
 * <p>
 * <b>Sin duplicar información:</b> cada {@link Arco} se crea una vez y se referencia desde
 * tres lugares: la lista de salientes del origen, la lista de entrantes del destino y la
 * {@link Arista} del par.
 * <p>
 * Toda relación existe en los dos sentidos: al crear {@code A → B}, se crea también
 * {@code B → A} como {@link TipoRelacion#DESCONOCIDO}, que luego puede cambiarse.
 */
public class Grafo {
    private final Map<Integer, Usuario> usuarios;
    private final ArrayList<Arista> aristas;
    private int siguienteId;

    /** Crea un grafo vacío. */
    public Grafo() {
        this.usuarios = new LinkedHashMap<>();
        this.aristas = new ArrayList<>();
        this.siguienteId = 1;
    }

    // Usuarios

    /**
     * Agrega un estudiante sin perfil, con el siguiente ID libre.
     *
     * @param nombre nombre del estudiante
     * @return el estudiante creado
     */
    public Usuario agregarUsuario(String nombre) {
        return agregarUsuario(nombre, PerfilEstudiante.VACIO);
    }

    /**
     * Agrega un estudiante con perfil, con el siguiente ID libre.
     *
     * @param nombre nombre del estudiante
     * @param perfil datos personales
     * @return el estudiante creado
     */
    public Usuario agregarUsuario(String nombre, PerfilEstudiante perfil) {
        Usuario usuario = new Usuario(siguienteId, nombre, perfil);
        usuarios.put(usuario.getId(), usuario);
        siguienteId++;
        return usuario;
    }

    /**
     * Agrega un estudiante sin perfil con un ID dado (usado al cargar JSON).
     *
     * @param id     ID mayor que 0 y no usado
     * @param nombre nombre del estudiante
     * @return el estudiante creado
     */
    public Usuario agregarUsuario(int id, String nombre) {
        return agregarUsuario(id, nombre, PerfilEstudiante.VACIO);
    }

    /**
     * Agrega un estudiante con un ID dado (usado al cargar JSON).
     *
     * @param id     ID mayor que 0 y no usado
     * @param nombre nombre del estudiante
     * @param perfil datos personales
     * @return el estudiante creado
     * @throws IllegalArgumentException si el ID no es válido o ya existe
     */
    public Usuario agregarUsuario(int id, String nombre, PerfilEstudiante perfil) {
        if (id <= 0) {
            throw new IllegalArgumentException("El ID debe ser mayor que 0");
        }
        if (usuarios.containsKey(id)) {
            throw new IllegalArgumentException("Ya existe un usuario con ID " + id);
        }
        Usuario usuario = new Usuario(id, nombre, perfil);
        usuarios.put(id, usuario);
        if (id >= siguienteId) {
            siguienteId = id + 1;
        }
        return usuario;
    }

    /**
     * Cambia los datos personales de un estudiante.
     *
     * @param id     ID del estudiante
     * @param perfil nuevos datos
     */
    public void actualizarPerfil(int id, PerfilEstudiante perfil) {
        obtenerUsuario(id).setPerfil(perfil);
    }

    /**
     * Elimina un estudiante y todas sus relaciones (en los dos sentidos).
     *
     * @param id ID del estudiante
     * @throws IllegalArgumentException si no existe
     */
    public void eliminarUsuario(int id) {
        Usuario usuario = obtenerUsuario(id);

        for (Arco arco : usuario.getSalientes()) {
            Usuario otro = usuarios.get(arco.getDestino());
            otro.eliminarSaliente(id);
            otro.eliminarEntrante(id);
        }
        aristas.removeIf(arista -> arista.contieneUsuario(id));
        usuarios.remove(id);
    }

    /** Borra todos los estudiantes y relaciones. */
    public void limpiar() {
        usuarios.clear();
        aristas.clear();
        siguienteId = 1;
    }

    // Relaciones

    /**
     * Crea la relación {@code origen → destino} con el peso por defecto del tipo.
     * El sentido contrario queda como {@link TipoRelacion#DESCONOCIDO}.
     *
     * @param origen  ID de quien opina
     * @param destino ID de sobre quien opina
     * @param tipo    tipo del sentido {@code origen → destino}
     */
    public void agregarRelacion(int origen, int destino, TipoRelacion tipo) {
        agregarRelacion(origen, destino, tipo, Objects.requireNonNull(tipo, "El tipo no puede ser null").getPesoPorDefecto());
    }

    /**
     * Crea la relación {@code origen → destino} con un peso (afinidad) dado.
     * El sentido contrario queda como {@link TipoRelacion#DESCONOCIDO} con su peso por defecto.
     *
     * @param origen  ID de quien opina
     * @param destino ID de sobre quien opina
     * @param tipo    tipo del sentido {@code origen → destino}
     * @param peso    afinidad del sentido {@code origen → destino}
     * @throws IllegalArgumentException si algún usuario no existe, si es el mismo usuario
     *                                  o si ya hay una relación entre ellos
     */
    public void agregarRelacion(int origen, int destino, TipoRelacion tipo, double peso) {
        Objects.requireNonNull(tipo, "El tipo no puede ser null");
        if (origen == destino) {
            throw new IllegalArgumentException("Un usuario no puede relacionarse consigo mismo");
        }
        Usuario usuarioOrigen = obtenerUsuario(origen);
        Usuario usuarioDestino = obtenerUsuario(destino);

        if (usuarioOrigen.tieneRelacionCon(destino)) {
            throw new IllegalArgumentException(
                "Ya existe una relación entre " + origen + " y " + destino
                + ". Usa cambiarTipoRelacion para modificarla"
            );
        }

        Arco arcoAB = new Arco(origen, destino, tipo, peso);
        Arco arcoBA = new Arco(destino, origen, TipoRelacion.DESCONOCIDO);

        // Cada arco se guarda por referencia en el origen (saliente) y en el destino (entrante)
        usuarioOrigen.agregarSaliente(arcoAB);
        usuarioDestino.agregarEntrante(arcoAB);
        usuarioDestino.agregarSaliente(arcoBA);
        usuarioOrigen.agregarEntrante(arcoBA);
        aristas.add(new Arista(arcoAB, arcoBA));
    }

    /**
     * Cambia el tipo de un sentido de la relación.
     *
     * @param origen    ID de quien opina
     * @param destino   ID de sobre quien opina
     * @param nuevoTipo tipo nuevo
     * @throws IllegalArgumentException si no hay relación o si ya tiene ese tipo
     */
    public void cambiarTipoRelacion(int origen, int destino, TipoRelacion nuevoTipo) {
        Objects.requireNonNull(nuevoTipo, "El tipo no puede ser null");
        Arco arco = obtenerArco(origen, destino);
        if (arco.getTipo() == nuevoTipo) {
            throw new IllegalArgumentException("La relación " + origen + " -> " + destino + " ya es " + nuevoTipo);
        }
        arco.setTipo(nuevoTipo);
    }

    /**
     * Cambia el peso (afinidad) de un sentido de la relación.
     *
     * @param origen  ID de quien opina
     * @param destino ID de sobre quien opina
     * @param peso    afinidad nueva
     * @throws IllegalArgumentException si no hay relación o el peso no es válido
     */
    public void cambiarPeso(int origen, int destino, double peso) {
        obtenerArco(origen, destino).setPeso(peso);
    }

    /**
     * Elimina los dos sentidos de la relación entre {@code a} y {@code b}.
     *
     * @param a ID de un estudiante
     * @param b ID del otro estudiante
     * @throws IllegalArgumentException si no hay relación
     */
    public void eliminarRelacion(int a, int b) {
        Usuario usuarioA = obtenerUsuario(a);
        Usuario usuarioB = obtenerUsuario(b);

        if (!usuarioA.tieneRelacionCon(b)) {
            throw new IllegalArgumentException("No existe una relación entre " + a + " y " + b);
        }
        usuarioA.eliminarSaliente(b);
        usuarioA.eliminarEntrante(b);
        usuarioB.eliminarSaliente(a);
        usuarioB.eliminarEntrante(a);
        aristas.removeIf(arista -> arista.contieneUsuario(a) && arista.contieneUsuario(b));
    }

    // Consultas

    /**
     * @param id ID a buscar
     * @return {@code true} si existe el estudiante
     */
    public boolean existeUsuario(int id) {
        return usuarios.containsKey(id);
    }

    /**
     * @param id ID a buscar
     * @return el estudiante, o {@code null} si no existe
     */
    public Usuario getUsuario(int id) {
        return usuarios.get(id);
    }

    /** @return copia de la lista de estudiantes, en orden de inserción */
    public List<Usuario> getUsuarios() {
        return new ArrayList<>(usuarios.values());
    }

    /**
     * @param origen  ID de quien opina
     * @param destino ID de sobre quien opina
     * @return {@code true} si existe el arco {@code origen → destino}
     */
    public boolean existeRelacion(int origen, int destino) {
        Usuario usuario = usuarios.get(origen);
        return usuario != null && usuario.tieneRelacionCon(destino);
    }

    /**
     * @param origen  ID de quien opina
     * @param destino ID de sobre quien opina
     * @return el arco {@code origen → destino}, o {@code null} si no existe
     */
    public Arco getArco(int origen, int destino) {
        Usuario usuario = usuarios.get(origen);
        return usuario == null ? null : usuario.getArcoHacia(destino);
    }

    /**
     * @param a ID de un estudiante
     * @param b ID del otro estudiante
     * @return la arista que los une, o {@code null} si no hay
     */
    public Arista getArista(int a, int b) {
        for (Arista arista : aristas) {
            if (arista.contieneUsuario(a) && arista.contieneUsuario(b)) return arista;
        }
        return null;
    }

    /** @return copia de la lista de aristas (pares de arcos) */
    public List<Arista> getAristas() {
        return new ArrayList<>(aristas);
    }

    /**
     * @param id ID del estudiante
     * @return cantidad de arcos salientes (de cualquier tipo)
     */
    public int getGradoSalida(int id) {
        return obtenerUsuario(id).getSalientes().size();
    }

    /**
     * @param id ID del estudiante
     * @return cantidad de arcos entrantes (de cualquier tipo)
     */
    public int getGradoEntrada(int id) {
        return obtenerUsuario(id).getEntrantes().size();
    }

    /** @return cantidad de estudiantes */
    public int getNumUsuarios() {
        return usuarios.size();
    }

    /** @return ID que recibirá el próximo estudiante agregado sin ID */
    public int getSiguienteId() {
        return siguienteId;
    }

    // Representación

    /** @return la lista de adyacencia como texto, con salientes y entrantes de cada vértice */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Grafo (").append(usuarios.size()).append(" usuarios, ")
          .append(aristas.size()).append(" pares de arcos)\n");
        for (Usuario usuario : usuarios.values()) {
            sb.append("  ").append(usuario).append(" — ").append(usuario.getPerfil()).append("\n");
            sb.append("     salientes: ").append(describirArcos(usuario.getSalientes())).append("\n");
            sb.append("     entrantes: ").append(describirArcos(usuario.getEntrantes())).append("\n");
        }
        return sb.toString();
    }

    private String describirArcos(List<Arco> arcos) {
        if (arcos.isEmpty()) return "(ninguno)";
        StringBuilder sb = new StringBuilder();
        for (Arco arco : arcos) {
            sb.append(arco).append(" ");
        }
        return sb.toString().trim();
    }

    // Validación interna

    private Usuario obtenerUsuario(int id) {
        Usuario usuario = usuarios.get(id);
        if (usuario == null) {
            throw new IllegalArgumentException("No existe el usuario con ID " + id);
        }
        return usuario;
    }

    private Arco obtenerArco(int origen, int destino) {
        Usuario usuarioOrigen = obtenerUsuario(origen);
        obtenerUsuario(destino);
        Arco arco = usuarioOrigen.getArcoHacia(destino);
        if (arco == null) {
            throw new IllegalArgumentException("No existe una relación entre " + origen + " y " + destino);
        }
        return arco;
    }
}
