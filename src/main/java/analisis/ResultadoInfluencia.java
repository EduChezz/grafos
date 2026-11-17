package analisis;

/**
 * Métricas de influencia de un estudiante, tal como las devuelve
 * {@link AnalizadorInfluencia#rankingInfluencia()}.
 */
public class ResultadoInfluencia {
    private final int idUsuario;
    private final String nombre;
    private final int gradoEntrada;
    private final int gradoSalida;
    private final double centralidadGrado;
    private final double centralidadIntermediacion;

    /**
     * @param idUsuario                 ID del estudiante
     * @param nombre                    nombre del estudiante
     * @param gradoEntrada              cuántos lo consideran amigo
     * @param gradoSalida               a cuántos considera amigos
     * @param centralidadGrado          grado de entrada / (n − 1)
     * @param centralidadIntermediacion intermediación normalizada (Brandes)
     */
    public ResultadoInfluencia(int idUsuario, String nombre, int gradoEntrada, int gradoSalida,
                               double centralidadGrado, double centralidadIntermediacion) {
        this.idUsuario = idUsuario;
        this.nombre = nombre;
        this.gradoEntrada = gradoEntrada;
        this.gradoSalida = gradoSalida;
        this.centralidadGrado = centralidadGrado;
        this.centralidadIntermediacion = centralidadIntermediacion;
    }

    /** @return ID del estudiante */
    public int getIdUsuario() {
        return idUsuario;
    }

    /** @return nombre del estudiante */
    public String getNombre() {
        return nombre;
    }

    /** @return cuántos lo consideran amigo */
    public int getGradoEntrada() {
        return gradoEntrada;
    }

    /** @return a cuántos considera amigos */
    public int getGradoSalida() {
        return gradoSalida;
    }

    /** @return grado de entrada / (n − 1), entre 0 y 1 */
    public double getCentralidadGrado() {
        return centralidadGrado;
    }

    /** @return intermediación normalizada, entre 0 y 1 */
    public double getCentralidadIntermediacion() {
        return centralidadIntermediacion;
    }

    @Override
    public String toString() {
        return nombre + " (" + idUsuario + ")"
            + " | intermediación=" + String.format("%.4f", centralidadIntermediacion)
            + " | entrada=" + gradoEntrada
            + " | salida=" + gradoSalida
            + " | centralidad de grado=" + String.format("%.4f", centralidadGrado);
    }
}
