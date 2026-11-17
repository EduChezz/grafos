package juego;

import java.util.Objects;

/**
 * Datos de juego de un sobreviviente: rol, paranoia y sala donde está.
 * <p>
 * <b>No es un vértice nuevo.</b> El vértice sigue siendo el {@link modelo.Usuario} (el estudiante),
 * con su nombre, perfil y listas de arcos. La ficha solo se asocia a él por su ID, así el juego
 * se monta encima del grafo sin duplicar la información ni modificar el modelo.
 */
public final class FichaSobreviviente {
    /** Paranoia máxima. */
    public static final int PARANOIA_MAXIMA = 100;

    private final int id;
    private Rol rol;
    private int paranoia;
    private Sala sala;

    /**
     * @param id       ID del estudiante (vértice) al que pertenece
     * @param rol      rol inicial
     * @param paranoia paranoia inicial (se recorta a 0..100)
     * @param sala     sala inicial
     */
    public FichaSobreviviente(int id, Rol rol, int paranoia, Sala sala) {
        this.id = id;
        this.rol = Objects.requireNonNull(rol, "El rol no puede ser null");
        this.sala = Objects.requireNonNull(sala, "La sala no puede ser null");
        setParanoia(paranoia);
    }

    /** @return ID del estudiante */
    public int getId() {
        return id;
    }

    /** @return rol actual */
    public Rol getRol() {
        return rol;
    }

    /** @param rol nuevo rol */
    public void setRol(Rol rol) {
        this.rol = Objects.requireNonNull(rol, "El rol no puede ser null");
    }

    /** @return paranoia, de 0 a 100 */
    public int getParanoia() {
        return paranoia;
    }

    /** @param paranoia nuevo valor; se recorta al rango 0..100 */
    public void setParanoia(int paranoia) {
        this.paranoia = Math.max(0, Math.min(PARANOIA_MAXIMA, paranoia));
    }

    /** @param cambio cantidad a sumar (o restar, si es negativa) */
    public void sumarParanoia(int cambio) {
        setParanoia(paranoia + cambio);
    }

    /** @return sala actual */
    public Sala getSala() {
        return sala;
    }

    /** @param sala nueva sala */
    public void setSala(Sala sala) {
        this.sala = Objects.requireNonNull(sala, "La sala no puede ser null");
    }
}
