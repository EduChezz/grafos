package juego;

/**
 * Emergencias que aparecen al azar en el refugio. Cada una necesita a alguien con un rol
 * específico, y se resuelve encontrando una <b>Ruta SOS</b> (BFS) hasta esa persona.
 */
public enum Crisis {
    /** Se necesita un ingeniero. */
    AVERIA_AGUA("¡El filtro de agua se averió!", Rol.INGENIERO),
    /** Se necesita un médico. */
    HERIDO("¡Hay un herido en la compuerta!", Rol.MEDICO),
    /** Se necesita un guardia. */
    INTRUSO("¡Ruidos extraños fuera de la compuerta!", Rol.GUARDIA),
    /** Se necesita un cocinero. */
    RACIONES("¡Las raciones se están pudriendo!", Rol.COCINERO);

    private final String descripcion;
    private final Rol rolNecesario;

    Crisis(String descripcion, Rol rolNecesario) {
        this.descripcion = descripcion;
        this.rolNecesario = rolNecesario;
    }

    /** @return texto de la alarma */
    public String getDescripcion() {
        return descripcion;
    }

    /** @return rol de la persona que puede resolverla */
    public Rol getRolNecesario() {
        return rolNecesario;
    }
}
