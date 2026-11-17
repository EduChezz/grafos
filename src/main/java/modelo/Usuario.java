package modelo;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Vértice del grafo: un estudiante de la red social.
 * <p>
 * Guarda su ID, su nombre, su {@link PerfilEstudiante perfil} y <b>dos listas de adyacencia</b>:
 * <ul>
 *   <li><b>salientes</b>: arcos {@code this → otro}, lo que este estudiante opina de los demás;</li>
 *   <li><b>entrantes</b>: arcos {@code otro → this}, lo que los demás opinan de él.</li>
 * </ul>
 * Las dos listas guardan <b>referencias</b> a los mismos objetos {@link Arco} que tiene el otro
 * extremo, así que no se duplica información. Tener los entrantes a mano permite calcular el
 * grado de entrada sin recorrer todo el grafo.
 */
public class Usuario {
    private final int id;
    private final String nombre;
    private PerfilEstudiante perfil;
    private final ArrayList<Arco> salientes;
    private final ArrayList<Arco> entrantes;

    /**
     * Crea un estudiante sin datos de perfil.
     *
     * @param id     identificador único
     * @param nombre nombre (no vacío)
     */
    public Usuario(int id, String nombre) {
        this(id, nombre, PerfilEstudiante.VACIO);
    }

    /**
     * Crea un estudiante con perfil.
     *
     * @param id     identificador único
     * @param nombre nombre (no vacío)
     * @param perfil datos personales; {@code null} equivale a {@link PerfilEstudiante#VACIO}
     * @throws IllegalArgumentException si el nombre está vacío
     */
    public Usuario(int id, String nombre, PerfilEstudiante perfil) {
        if (nombre == null || nombre.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre no puede estar vacío");
        }
        this.id = id;
        this.nombre = nombre.trim();
        this.perfil = perfil == null ? PerfilEstudiante.VACIO : perfil;
        this.salientes = new ArrayList<>();
        this.entrantes = new ArrayList<>();
    }

    /** @return identificador único */
    public int getId() {
        return id;
    }

    /** @return nombre del estudiante */
    public String getNombre() {
        return nombre;
    }

    /** @return datos personales del estudiante */
    public PerfilEstudiante getPerfil() {
        return perfil;
    }

    /**
     * @param perfil nuevos datos personales; {@code null} los borra
     */
    public void setPerfil(PerfilEstudiante perfil) {
        this.perfil = perfil == null ? PerfilEstudiante.VACIO : perfil;
    }

    // Salientes

    /** @return copia de la lista de arcos salientes ({@code this → otro}) */
    public List<Arco> getSalientes() {
        return new ArrayList<>(salientes);
    }

    /**
     * @param tipo tipo de relación a filtrar
     * @return arcos salientes de ese tipo
     */
    public List<Arco> getSalientes(TipoRelacion tipo) {
        return filtrar(salientes, tipo);
    }

    /**
     * @param destino ID del otro estudiante
     * @return el arco {@code this → destino}, o {@code null} si no existe
     */
    public Arco getArcoHacia(int destino) {
        for (Arco arco : salientes) {
            if (arco.getDestino() == destino) return arco;
        }
        return null;
    }

    /**
     * @param destino ID del otro estudiante
     * @return {@code true} si existe el arco {@code this → destino}
     */
    public boolean tieneRelacionCon(int destino) {
        return getArcoHacia(destino) != null;
    }

    /**
     * Agrega una referencia a un arco saliente.
     *
     * @param arco arco cuyo origen debe ser este estudiante
     * @throws IllegalArgumentException si el origen no coincide
     */
    public void agregarSaliente(Arco arco) {
        Objects.requireNonNull(arco, "El arco no puede ser null");
        if (arco.getOrigen() != id) {
            throw new IllegalArgumentException("El origen del arco no coincide con el usuario " + id);
        }
        salientes.add(arco);
    }

    /**
     * @param destino ID del otro estudiante
     * @return {@code true} si se eliminó el arco {@code this → destino}
     */
    public boolean eliminarSaliente(int destino) {
        return salientes.removeIf(arco -> arco.getDestino() == destino);
    }

    // Entrantes

    /** @return copia de la lista de arcos entrantes ({@code otro → this}) */
    public List<Arco> getEntrantes() {
        return new ArrayList<>(entrantes);
    }

    /**
     * @param tipo tipo de relación a filtrar
     * @return arcos entrantes de ese tipo
     */
    public List<Arco> getEntrantes(TipoRelacion tipo) {
        return filtrar(entrantes, tipo);
    }

    /**
     * @param origen ID del otro estudiante
     * @return el arco {@code origen → this}, o {@code null} si no existe
     */
    public Arco getArcoDesde(int origen) {
        for (Arco arco : entrantes) {
            if (arco.getOrigen() == origen) return arco;
        }
        return null;
    }

    /**
     * Agrega una referencia a un arco entrante.
     *
     * @param arco arco cuyo destino debe ser este estudiante
     * @throws IllegalArgumentException si el destino no coincide
     */
    public void agregarEntrante(Arco arco) {
        Objects.requireNonNull(arco, "El arco no puede ser null");
        if (arco.getDestino() != id) {
            throw new IllegalArgumentException("El destino del arco no coincide con el usuario " + id);
        }
        entrantes.add(arco);
    }

    /**
     * @param origen ID del otro estudiante
     * @return {@code true} si se eliminó el arco {@code origen → this}
     */
    public boolean eliminarEntrante(int origen) {
        return entrantes.removeIf(arco -> arco.getOrigen() == origen);
    }

    private static List<Arco> filtrar(List<Arco> arcos, TipoRelacion tipo) {
        List<Arco> filtrados = new ArrayList<>();
        for (Arco arco : arcos) {
            if (arco.getTipo() == tipo) filtrados.add(arco);
        }
        return filtrados;
    }

    @Override
    public String toString() {
        return nombre + " (" + id + ")";
    }

    /** Dos estudiantes son iguales si tienen el mismo ID. */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Usuario)) return false;
        return id == ((Usuario) obj).id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }
}
