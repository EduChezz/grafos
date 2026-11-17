package analisis;

import java.util.ArrayList;
import java.util.List;

/**
 * Comunidad detectada en la red: un grupo de estudiantes unidos por triángulos de amistad mutua.
 * <p>
 * Además de sus miembros, mide <b>qué tan fuerte es</b>. Una comunidad es más fuerte cuanto
 * más se acerca a un grafo completo, donde todos son amigos de todos. En un grupo de n
 * estudiantes hay n·(n−1)/2 parejas posibles; la <b>densidad</b> es la fracción de esas parejas
 * que tienen amistad mutua:
 * <pre>densidad = conexiones / (n·(n−1)/2)</pre>
 * Una densidad de 1 significa que la comunidad es una <b>clique</b> (todos con todos).
 * El <b>núcleo</b> es la clique más grande dentro de la comunidad.
 */
public class Comunidad {

    /** Clasificación de la fuerza de una comunidad según su densidad. */
    public enum Fuerza {
        /** Densidad 1: todos son amigos de todos (clique). */
        COMPLETA("Completa", 1.0),
        /** Densidad de 0.75 o más. */
        FUERTE("Fuerte", 0.75),
        /** Densidad de 0.5 o más. */
        MODERADA("Moderada", 0.5),
        /** Densidad menor que 0.5. */
        DEBIL("Débil", 0.0);

        private final String etiqueta;
        private final double densidadMinima;

        Fuerza(String etiqueta, double densidadMinima) {
            this.etiqueta = etiqueta;
            this.densidadMinima = densidadMinima;
        }

        /** @return nombre legible */
        public String getEtiqueta() {
            return etiqueta;
        }

        /** @return densidad mínima para tener esta fuerza */
        public double getDensidadMinima() {
            return densidadMinima;
        }

        /**
         * @param densidad valor entre 0 y 1
         * @return la clasificación que corresponde
         */
        public static Fuerza desde(double densidad) {
            for (Fuerza fuerza : values()) {
                if (densidad >= fuerza.densidadMinima - 1e-9) return fuerza;
            }
            return DEBIL;
        }
    }

    private final int id;
    private final ArrayList<Integer> miembros;
    private final ArrayList<int[]> triangulos;
    private final int conexiones;
    private final ArrayList<Integer> nucleo;
    private final double afinidadPromedio;

    /**
     * @param id               número de la comunidad (1, 2, ...)
     * @param miembros         IDs de los miembros
     * @param triangulos       triángulos de amistad mutua que la forman
     * @param conexiones       parejas de miembros con amistad mutua
     * @param nucleo           IDs de la clique más grande dentro de la comunidad
     * @param afinidadPromedio peso promedio de los arcos de amistad internos
     */
    public Comunidad(int id, List<Integer> miembros, List<int[]> triangulos,
                     int conexiones, List<Integer> nucleo, double afinidadPromedio) {
        this.id = id;
        this.miembros = new ArrayList<>(miembros);
        this.triangulos = new ArrayList<>(triangulos);
        this.conexiones = conexiones;
        this.nucleo = new ArrayList<>(nucleo);
        this.afinidadPromedio = afinidadPromedio;
    }

    /** @return número de la comunidad */
    public int getId() {
        return id;
    }

    /** @return copia de los IDs de los miembros, de menor a mayor */
    public List<Integer> getMiembros() {
        return new ArrayList<>(miembros);
    }

    /** @return copia de los triángulos que la forman */
    public List<int[]> getTriangulos() {
        return new ArrayList<>(triangulos);
    }

    /** @return cantidad de miembros */
    public int getTamano() {
        return miembros.size();
    }

    /** @return parejas de miembros con amistad mutua */
    public int getConexiones() {
        return conexiones;
    }

    /** @return parejas posibles entre los miembros: n·(n−1)/2 */
    public int getConexionesPosibles() {
        int n = miembros.size();
        return n * (n - 1) / 2;
    }

    /** @return conexiones / conexiones posibles, entre 0 y 1 */
    public double getDensidad() {
        int posibles = getConexionesPosibles();
        return posibles == 0 ? 0.0 : (double) conexiones / posibles;
    }

    /** @return clasificación según la densidad */
    public Fuerza getFuerza() {
        return Fuerza.desde(getDensidad());
    }

    /** @return copia de los IDs de la clique más grande dentro de la comunidad */
    public List<Integer> getNucleo() {
        return new ArrayList<>(nucleo);
    }

    /** @return peso promedio de los arcos de amistad entre miembros */
    public double getAfinidadPromedio() {
        return afinidadPromedio;
    }

    /**
     * @param idUsuario ID a buscar
     * @return {@code true} si es miembro
     */
    public boolean contiene(int idUsuario) {
        return miembros.contains(idUsuario);
    }

    @Override
    public String toString() {
        return "Comunidad " + id + " " + miembros + " densidad=" + String.format("%.2f", getDensidad())
            + " (" + getFuerza().getEtiqueta() + ")";
    }
}
