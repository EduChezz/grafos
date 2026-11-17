package modelo;

import java.util.regex.Pattern;

/**
 * Datos personales de un estudiante (la información que guarda cada vértice además de su ID y nombre).
 * <p>
 * Todos los campos son opcionales: un texto vacío o un número 0 significa "sin dato".
 * Es inmutable y se valida al construirse, así un vértice nunca queda con datos inválidos.
 *
 * @param carrera  carrera que cursa el estudiante ("" si no se indica)
 * @param semestre semestre actual, de 1 a 12 (0 si no se indica)
 * @param edad     edad en años, de 1 a 120 (0 si no se indica)
 * @param correo   correo electrónico ("" si no se indica)
 */
public record PerfilEstudiante(String carrera, int semestre, int edad, String correo) {

    /** Perfil sin ningún dato. */
    public static final PerfilEstudiante VACIO = new PerfilEstudiante("", 0, 0, "");

    /** Semestre máximo permitido. */
    public static final int SEMESTRE_MAXIMO = 12;

    /** Edad máxima permitida. */
    public static final int EDAD_MAXIMA = 120;

    private static final Pattern CORREO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    /**
     * Normaliza los textos y valida los rangos.
     *
     * @throws IllegalArgumentException si el semestre, la edad o el correo no son válidos
     */
    public PerfilEstudiante {
        carrera = carrera == null ? "" : carrera.trim();
        correo = correo == null ? "" : correo.trim();
        if (semestre < 0 || semestre > SEMESTRE_MAXIMO) {
            throw new IllegalArgumentException("El semestre debe estar entre 1 y " + SEMESTRE_MAXIMO);
        }
        if (edad < 0 || edad > EDAD_MAXIMA) {
            throw new IllegalArgumentException("La edad debe estar entre 1 y " + EDAD_MAXIMA);
        }
        if (!correo.isEmpty() && !CORREO.matcher(correo).matches()) {
            throw new IllegalArgumentException("El correo no es válido: " + correo);
        }
    }

    /**
     * @return {@code true} si el perfil no tiene ningún dato
     */
    public boolean estaVacio() {
        return carrera.isEmpty() && semestre == 0 && edad == 0 && correo.isEmpty();
    }

    /**
     * @return los datos presentes separados por " · ", o "(sin datos)" si está vacío
     */
    @Override
    public String toString() {
        if (estaVacio()) return "(sin datos)";
        StringBuilder sb = new StringBuilder();
        if (!carrera.isEmpty()) agregar(sb, carrera);
        if (semestre > 0) agregar(sb, semestre + "° semestre");
        if (edad > 0) agregar(sb, edad + " años");
        if (!correo.isEmpty()) agregar(sb, correo);
        return sb.toString();
    }

    private static void agregar(StringBuilder sb, String texto) {
        if (sb.length() > 0) sb.append(" · ");
        sb.append(texto);
    }
}
