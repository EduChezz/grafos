package juego;

import analisis.AnalizadorRed;
import modelo.Arco;
import modelo.Grafo;
import modelo.TipoRelacion;
import modelo.Usuario;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.HashSet;

/**
 * Motor del juego <b>Refugio AP-4</b>: los estudiantes del curso conviven en un búnker y su red
 * de confianza (el grafo dirigido) cambia con cada turno.
 * <p>
 * El refugio <b>no guarda otro grafo</b>: cada turno lee y modifica el mismo {@link Grafo} de la
 * aplicación usando sus operaciones públicas (crear relación, cambiar tipo, cambiar peso). Aquí
 * solo viven los datos de juego de cada estudiante ({@link FichaSobreviviente}).
 * <p>
 * <b>Reglas de un turno</b>
 * <ol>
 *   <li><b>Movimiento</b>: 45% va a su sala de trabajo, 15% al comedor, 15% a los dormitorios, 12% al invernadero
 *       y 13% a una sala al azar.</li>
 *   <li><b>Enfermería</b>: si hay un médico, los demás que estén ahí bajan 12 de paranoia.</li>
 *   <li><b>Traición</b>: quien llega al filtro de agua con paranoia mayor a {@value #PARANOIA_TRAICION}
 *       convierte en Enemistad su arco de confianza más débil.</li>
 *   <li><b>Sospecha</b>: con paranoia mayor a {@value #PARANOIA_SOSPECHA}, al cruzarse con alguien
 *       puede desconfiar de él (10% + 1% por cada punto de paranoia sobre {@value #PARANOIA_SOSPECHA}).</li>
 *   <li><b>Encuentros</b>: cada pareja que coincide en una sala puede crear confianza
 *       (comedor 40%, dormitorios 15%, otras 25%; +15% si tienen amigos en común; a la mitad si quien
 *       confía tiene paranoia mayor a 70). Si la confianza ya es mutua, la afinidad sube 1.</li>
 *   <li><b>Enemigos que se cruzan</b>: si tienen amigos en común, 50% de que uno de ellos medie y la
 *       Enemistad vuelva a Desconocido; si no, 35% de pelea (+12 de paranoia a ambos).</li>
 *   <li><b>Paranoia</b>: +6 por racionamiento; +10 si nadie confía en él; −2 por cada persona que
 *       confía en él (máximo −8); −6 si durmió; −4 si estuvo en el invernadero; +3 mientras haya una crisis activa.</li>
 *   <li><b>Crisis</b>: desde el turno 2, 25% de que aparezca una emergencia que se resuelve con la
 *       Ruta SOS. Cada 3 turnos sin resolver, todos suben 8 de paranoia.</li>
 * </ol>
 * Con la misma semilla, la misma red y las mismas acciones, la partida se repite igual.
 */
public class Refugio {
    /** Paranoia a partir de la cual alguien traiciona en el filtro de agua. */
    public static final int PARANOIA_TRAICION = 80;
    /** Paranoia a partir de la cual alguien puede sospechar de quien se cruza. */
    public static final int PARANOIA_SOSPECHA = 55;
    /** Afinidad con la que nace una confianza nueva. */
    public static final double AFINIDAD_INICIAL = 3.0;
    /** Semilla por defecto. */
    public static final long SEMILLA_POR_DEFECTO = 2026L;

    private static final Rol[] ROTACION = {Rol.INGENIERO, Rol.MEDICO, Rol.GUARDIA, Rol.COCINERO};

    private final Map<Integer, FichaSobreviviente> fichas = new LinkedHashMap<>();
    private Random azar;
    private long semilla;
    private int turno;
    private int rolesAsignados;
    private Crisis crisis;
    private int turnosCrisis;

    /** Crea un refugio con la semilla por defecto. */
    public Refugio() {
        this.semilla = SEMILLA_POR_DEFECTO;
        this.azar = new Random(semilla);
    }

    // Preparación

    /**
     * Borra las fichas y empieza una partida nueva sobre el grafo actual.
     *
     * @param grafo   grafo de la aplicación
     * @param semilla semilla del azar (la misma semilla repite la partida)
     * @return eventos iniciales (por ejemplo, quién asumió el mando)
     */
    public List<Evento> reiniciar(Grafo grafo, long semilla) {
        fichas.clear();
        this.semilla = semilla;
        this.azar = new Random(semilla);
        turno = 0;
        rolesAsignados = 0;
        crisis = null;
        turnosCrisis = 0;
        return sincronizar(grafo);
    }

    /**
     * Pone al día las fichas con el grafo: crea las de estudiantes nuevos, borra las de los
     * eliminados y se asegura de que haya una líder.
     *
     * @param grafo grafo de la aplicación
     * @return eventos generados (solo si cambió la líder)
     */
    public List<Evento> sincronizar(Grafo grafo) {
        List<Evento> eventos = new ArrayList<>();
        fichas.keySet().removeIf(id -> !grafo.existeUsuario(id));
        for (Usuario usuario : grafo.getUsuarios()) {
            if (fichas.containsKey(usuario.getId())) continue;
            Rol rol = hayLider() ? ROTACION[rolesAsignados++ % ROTACION.length] : Rol.LIDER;
            int paranoia = 20 + azar.nextInt(50);
            fichas.put(usuario.getId(), new FichaSobreviviente(usuario.getId(), rol, paranoia, rol.getSalaTrabajo()));
        }
        if (!fichas.isEmpty() && !hayLider()) {
            int nuevo = masConfiable(grafo);
            fichas.get(nuevo).setRol(Rol.LIDER);
            eventos.add(new Evento("MANDO",
                nombre(grafo, nuevo) + " asume el mando: es en quien más confían.", List.of(nuevo), null));
        }
        return eventos;
    }

    private boolean hayLider() {
        return getIdLider() != -1;
    }

    /** Estudiante con más arcos de confianza entrantes (en empate, el de ID menor). */
    private int masConfiable(Grafo grafo) {
        int mejor = -1;
        int mejorGrado = -1;
        for (int id : fichas.keySet()) {
            int grado = grafo.getUsuario(id).getEntrantes(TipoRelacion.AMISTAD).size();
            if (grado > mejorGrado || (grado == mejorGrado && id < mejor)) {
                mejor = id;
                mejorGrado = grado;
            }
        }
        return mejor;
    }

    // Turno

    /**
     * Avanza un turno: mueve a todos, resuelve los encuentros y actualiza la paranoia.
     *
     * @param grafo grafo de la aplicación (se modifica)
     * @return lo que pasó, en orden
     */
    public List<Evento> avanzarTurno(Grafo grafo) {
        List<Evento> eventos = new ArrayList<>(sincronizar(grafo));
        turno++;
        if (fichas.isEmpty()) return eventos;

        mover();
        Map<Sala, List<Integer>> ocupantes = ocupantes();
        Set<Integer> ocupados = new HashSet<>();
        AnalizadorRed red = new AnalizadorRed(grafo);
        Map<Integer, Integer> paranoiaAntes = new LinkedHashMap<>();
        for (FichaSobreviviente ficha : fichas.values()) {
            paranoiaAntes.put(ficha.getId(), ficha.getParanoia());
        }

        for (Sala sala : Sala.values()) {
            List<Integer> aqui = ocupantes.get(sala);
            if (aqui.isEmpty()) continue;
            if (sala == Sala.ENFERMERIA) atenderEnfermeria(grafo, aqui, eventos);
            if (sala == Sala.FILTRO_AGUA) traiciones(grafo, aqui, ocupados, eventos);
            for (int i = 0; i < aqui.size(); i++) {
                for (int j = i + 1; j < aqui.size(); j++) {
                    encuentro(grafo, red, sala, aqui.get(i), aqui.get(j), ocupados, eventos);
                }
            }
        }

        actualizarParanoia(grafo, paranoiaAntes, eventos);
        gestionarCrisis(eventos);
        return eventos;
    }

    private void mover() {
        Sala[] salas = Sala.values();
        for (FichaSobreviviente ficha : fichas.values()) {
            double tirada = azar.nextDouble();
            Sala destino;
            if (tirada < 0.45) destino = ficha.getRol().getSalaTrabajo();
            else if (tirada < 0.60) destino = Sala.COMEDOR;
            else if (tirada < 0.75) destino = Sala.DORMITORIOS;
            else if (tirada < 0.87) destino = Sala.INVERNADERO;
            else destino = salas[azar.nextInt(salas.length)];
            ficha.setSala(destino);
        }
    }

    /** @return para cada sala, los IDs que están en ella, en orden aleatorio */
    private Map<Sala, List<Integer>> ocupantes() {
        Map<Sala, List<Integer>> ocupantes = new EnumMap<>(Sala.class);
        for (Sala sala : Sala.values()) {
            ocupantes.put(sala, new ArrayList<>());
        }
        for (FichaSobreviviente ficha : fichas.values()) {
            ocupantes.get(ficha.getSala()).add(ficha.getId());
        }
        for (List<Integer> lista : ocupantes.values()) {
            Collections.shuffle(lista, azar);
        }
        return ocupantes;
    }

    private void atenderEnfermeria(Grafo grafo, List<Integer> aqui, List<Evento> eventos) {
        Integer medico = null;
        for (int id : aqui) {
            if (fichas.get(id).getRol() == Rol.MEDICO) {
                medico = id;
                break;
            }
        }
        if (medico == null) return;
        List<Integer> atendidos = new ArrayList<>();
        for (int id : aqui) {
            if (id != medico && fichas.get(id).getParanoia() > 0) {
                fichas.get(id).sumarParanoia(-12);
                atendidos.add(id);
            }
        }
        if (atendidos.isEmpty()) return;
        List<Integer> ids = new ArrayList<>();
        ids.add(medico);
        ids.addAll(atendidos);
        eventos.add(new Evento("CALMA",
            nombre(grafo, medico) + " atiende en la enfermería a " + nombres(grafo, atendidos) + ": baja su paranoia.",
            ids, Sala.ENFERMERIA));
    }

    private void traiciones(Grafo grafo, List<Integer> aqui, Set<Integer> ocupados, List<Evento> eventos) {
        for (int id : aqui) {
            FichaSobreviviente ficha = fichas.get(id);
            if (ficha.getParanoia() <= PARANOIA_TRAICION) continue;
            List<Arco> confianzas = grafo.getUsuario(id).getSalientes(TipoRelacion.AMISTAD);
            if (confianzas.isEmpty()) continue;

            Arco masDebil = confianzas.get(0);
            for (Arco arco : confianzas) {
                if (arco.getPeso() < masDebil.getPeso()) masDebil = arco;
            }
            int victima = masDebil.getDestino();
            grafo.cambiarTipoRelacion(id, victima, TipoRelacion.ENEMISTAD);
            ficha.sumarParanoia(-25);
            ocupados.add(id);
            eventos.add(new Evento("TRAICION",
                "¡TRAICIÓN! Con paranoia " + (ficha.getParanoia() + 25) + ", " + nombre(grafo, id)
                    + " rompe su confianza en " + nombre(grafo, victima) + ": ahora es Enemistad.",
                List.of(id, victima), Sala.FILTRO_AGUA));
        }
    }

    private void encuentro(Grafo grafo, AnalizadorRed red, Sala sala, int a, int b,
                           Set<Integer> ocupados, List<Evento> eventos) {
        if (ocupados.contains(a) || ocupados.contains(b)) return;
        Arco ab = grafo.getArco(a, b);
        Arco ba = grafo.getArco(b, a);
        boolean hayEnemistad = (ab != null && ab.getTipo() == TipoRelacion.ENEMISTAD)
            || (ba != null && ba.getTipo() == TipoRelacion.ENEMISTAD);
        List<Integer> comunes = red.amigosEnComun(a, b);

        if (hayEnemistad) {
            enemigosSeCruzan(grafo, sala, a, b, ab, ba, comunes, ocupados, eventos);
            return;
        }

        // Quién da el paso (de confiar o de sospechar) se decide al azar
        int x = azar.nextBoolean() ? a : b;
        int y = x == a ? b : a;
        if (sospecha(grafo, sala, x, y, ocupados, eventos)) return;

        double probabilidad = sala == Sala.COMEDOR ? 0.40 : sala == Sala.DORMITORIOS ? 0.15 : 0.25;
        if (!comunes.isEmpty()) probabilidad += 0.15;
        if (fichas.get(x).getParanoia() > 70) probabilidad /= 2;
        if (azar.nextDouble() >= probabilidad) return;

        Arco xy = grafo.getArco(x, y);
        Arco yx = grafo.getArco(y, x);
        String lugar = " en " + sala.getEtiqueta().toLowerCase() + ".";

        if (xy == null) {
            grafo.agregarRelacion(x, y, TipoRelacion.AMISTAD, AFINIDAD_INICIAL);
            eventos.add(new Evento("CONFIANZA",
                nombre(grafo, x) + " empieza a confiar en " + nombre(grafo, y) + lugar, List.of(x, y), sala));
        } else if (xy.getTipo() == TipoRelacion.DESCONOCIDO) {
            grafo.cambiarTipoRelacion(x, y, TipoRelacion.AMISTAD);
            boolean mutua = yx.getTipo() == TipoRelacion.AMISTAD;
            eventos.add(new Evento(mutua ? "ALIANZA" : "CONFIANZA",
                mutua ? "¡" + nombre(grafo, x) + " corresponde a " + nombre(grafo, y) + ": nace una alianza!"
                      : nombre(grafo, x) + " empieza a confiar en " + nombre(grafo, y) + lugar,
                List.of(x, y), sala));
        } else if (yx.getTipo() != TipoRelacion.AMISTAD) {
            // x ya confía en y; y decide si corresponde
            if (fichas.get(y).getParanoia() >= 60) return;
            grafo.cambiarTipoRelacion(y, x, TipoRelacion.AMISTAD);
            eventos.add(new Evento("ALIANZA",
                "¡" + nombre(grafo, y) + " corresponde a " + nombre(grafo, x) + ": nace una alianza!", List.of(x, y), sala));
        } else {
            boolean subio = subirAfinidad(xy) | subirAfinidad(yx);
            if (!subio) return;
            eventos.add(new Evento("FORTALECE",
                "La alianza de " + nombre(grafo, x) + " y " + nombre(grafo, y) + " se fortalece (afinidad "
                    + (int) xy.getPeso() + ")" + lugar.substring(0, lugar.length() - 1) + ".",
                List.of(x, y), sala));
        }
        ocupados.add(x);
        ocupados.add(y);
    }

    /**
     * Regla de sospecha: con paranoia mayor a {@value #PARANOIA_SOSPECHA}, {@code x} puede desconfiar
     * de {@code y}. Si no lo conocía, lo ve como enemigo; si confiaba poco, deja de confiar; si confiaba
     * mucho, la afinidad baja 2.
     *
     * @return {@code true} si hubo sospecha
     */
    private boolean sospecha(Grafo grafo, Sala sala, int x, int y, Set<Integer> ocupados, List<Evento> eventos) {
        int paranoia = fichas.get(x).getParanoia();
        if (paranoia <= PARANOIA_SOSPECHA) return false;
        double probabilidad = 0.10 + (paranoia - PARANOIA_SOSPECHA) / 100.0;
        if (azar.nextDouble() >= probabilidad) return false;

        Arco xy = grafo.getArco(x, y);
        String texto;
        if (xy == null) {
            grafo.agregarRelacion(x, y, TipoRelacion.ENEMISTAD, 1.0);
            texto = nombre(grafo, x) + " (paranoia " + paranoia + ") sospecha de " + nombre(grafo, y) + ": nace una Enemistad.";
        } else if (xy.getTipo() == TipoRelacion.DESCONOCIDO) {
            xy.setTipo(TipoRelacion.ENEMISTAD);
            texto = nombre(grafo, x) + " (paranoia " + paranoia + ") sospecha de " + nombre(grafo, y) + ": ahora es Enemistad.";
        } else if (xy.getPeso() <= 3) {
            xy.setTipo(TipoRelacion.DESCONOCIDO);
            texto = nombre(grafo, x) + " (paranoia " + paranoia + ") duda de " + nombre(grafo, y) + " y deja de confiar.";
        } else {
            xy.setPeso(xy.getPeso() - 2);
            texto = nombre(grafo, x) + " (paranoia " + paranoia + ") desconfía un poco de " + nombre(grafo, y)
                + " (afinidad baja a " + (int) xy.getPeso() + ").";
        }
        fichas.get(y).sumarParanoia(5);
        ocupados.add(x);
        ocupados.add(y);
        eventos.add(new Evento("SOSPECHA", texto, List.of(x, y), sala));
        return true;
    }

    private boolean subirAfinidad(Arco arco) {
        if (arco.getPeso() >= Arco.PESO_MAXIMO) return false;
        arco.setPeso(Math.min(Arco.PESO_MAXIMO, arco.getPeso() + 1));
        return true;
    }

    private void enemigosSeCruzan(Grafo grafo, Sala sala, int a, int b, Arco ab, Arco ba, List<Integer> comunes,
                                  Set<Integer> ocupados, List<Evento> eventos) {
        if (!comunes.isEmpty() && azar.nextDouble() < 0.5) {
            int mediador = comunes.get(azar.nextInt(comunes.size()));
            if (ab.getTipo() == TipoRelacion.ENEMISTAD) ab.setTipo(TipoRelacion.DESCONOCIDO);
            if (ba.getTipo() == TipoRelacion.ENEMISTAD) ba.setTipo(TipoRelacion.DESCONOCIDO);
            eventos.add(new Evento("MEDIACION",
                nombre(grafo, mediador) + ", amigo en común, media entre " + nombre(grafo, a) + " y "
                    + nombre(grafo, b) + ": la enemistad se calma.",
                List.of(a, b, mediador), sala));
        } else if (azar.nextDouble() < 0.35) {
            fichas.get(a).sumarParanoia(12);
            fichas.get(b).sumarParanoia(12);
            eventos.add(new Evento("CONFLICTO",
                "Pelea entre " + nombre(grafo, a) + " y " + nombre(grafo, b) + " en "
                    + sala.getEtiqueta().toLowerCase() + ". Sube la paranoia de ambos.",
                List.of(a, b), sala));
        } else {
            return;
        }
        ocupados.add(a);
        ocupados.add(b);
    }

    private void actualizarParanoia(Grafo grafo, Map<Integer, Integer> antes, List<Evento> eventos) {
        for (FichaSobreviviente ficha : fichas.values()) {
            int confianRecibida = grafo.getUsuario(ficha.getId()).getEntrantes(TipoRelacion.AMISTAD).size();
            int cambio = 6 + (confianRecibida == 0 ? 10 : -Math.min(2 * confianRecibida, 8));
            if (ficha.getSala() == Sala.DORMITORIOS) cambio -= 6;
            if (ficha.getSala() == Sala.INVERNADERO) cambio -= 4;
            if (crisis != null) cambio += 3;
            ficha.sumarParanoia(cambio);

            if (antes.get(ficha.getId()) <= PARANOIA_TRAICION && ficha.getParanoia() > PARANOIA_TRAICION) {
                eventos.add(new Evento("ALERTA",
                    nombre(grafo, ficha.getId()) + " está al borde (paranoia " + ficha.getParanoia()
                        + "). Si llega al filtro de agua, traicionará.",
                    List.of(ficha.getId()), ficha.getSala()));
            }
        }
    }

    private void gestionarCrisis(List<Evento> eventos) {
        if (crisis == null) {
            if (turno >= 2 && azar.nextDouble() < 0.25) {
                Crisis[] posibles = Crisis.values();
                crisis = posibles[azar.nextInt(posibles.length)];
                turnosCrisis = 0;
                eventos.add(new Evento("CRISIS",
                    crisis.getDescripcion() + " Se necesita un " + crisis.getRolNecesario().getEtiqueta().toLowerCase()
                        + ": usa la Ruta SOS.",
                    List.of(), null));
            }
            return;
        }
        turnosCrisis++;
        if (turnosCrisis % 3 == 0) {
            for (FichaSobreviviente ficha : fichas.values()) {
                ficha.sumarParanoia(8);
            }
            eventos.add(new Evento("CRISIS",
                "La emergencia sigue sin resolverse (" + turnosCrisis + " turnos). Sube la tensión en todo el refugio.",
                List.of(), null));
        }
    }

    // Ruta SOS

    /**
     * Ejecuta la Ruta SOS y, si llega a alguien con el rol que pide la crisis activa, la resuelve.
     *
     * @param grafo  grafo de la aplicación
     * @param origen quien pide ayuda
     * @param rol    rol que se busca
     * @param eventos lista donde se agregan los eventos producidos
     * @return resultado del BFS
     */
    public AnalizadorRefugio.ResultadoRuta rutaSOS(Grafo grafo, int origen, Rol rol, List<Evento> eventos) {
        Set<Integer> objetivos = new HashSet<>();
        for (FichaSobreviviente ficha : fichas.values()) {
            if (ficha.getRol() == rol) objetivos.add(ficha.getId());
        }
        AnalizadorRefugio.ResultadoRuta ruta = new AnalizadorRefugio(grafo).rutaSOS(origen, objetivos);

        if (!ruta.encontrada()) {
            eventos.add(new Evento("SOS",
                "Ruta SOS fallida: ninguna cadena de confianza lleva de " + nombre(grafo, origen) + " a un "
                    + rol.getEtiqueta().toLowerCase() + ".",
                List.of(origen), null));
            return ruta;
        }
        eventos.add(new Evento("SOS",
            "Ruta SOS: " + nombres(grafo, ruta.camino(), " ➔ ") + " (" + (ruta.camino().size() - 1) + " saltos).",
            ruta.camino(), null));
        if (crisis != null && crisis.getRolNecesario() == rol) {
            for (FichaSobreviviente ficha : fichas.values()) {
                ficha.sumarParanoia(-10);
            }
            int ayudante = ruta.camino().get(ruta.camino().size() - 1);
            eventos.add(new Evento("RESUELTA",
                nombre(grafo, ayudante) + " llegó a tiempo: emergencia resuelta. El refugio respira aliviado.",
                List.of(ayudante), null));
            crisis = null;
            turnosCrisis = 0;
        }
        return ruta;
    }

    // Edición

    /**
     * Cambia el rol de un sobreviviente. Si se nombra una nueva líder, la anterior pasa a guardia.
     *
     * @param id  ID del estudiante
     * @param rol rol nuevo
     */
    public void cambiarRol(int id, Rol rol) {
        FichaSobreviviente ficha = obtener(id);
        if (rol == Rol.LIDER) {
            int anterior = getIdLider();
            if (anterior != -1 && anterior != id) fichas.get(anterior).setRol(Rol.GUARDIA);
        } else if (ficha.getRol() == Rol.LIDER) {
            throw new IllegalArgumentException("Para quitarle el mando, nombra primero a otra persona como líder");
        }
        ficha.setRol(rol);
    }

    /**
     * @param id       ID del estudiante
     * @param paranoia valor nuevo (0..100)
     */
    public void cambiarParanoia(int id, int paranoia) {
        obtener(id).setParanoia(paranoia);
    }

    // Consultas

    /**
     * Estabilidad del refugio, de 0 a 100: 55% depende de cuántos tienen al menos una alianza
     * (confianza mutua) y 45% de la calma promedio (100 − paranoia).
     *
     * @param grafo grafo de la aplicación
     * @return estabilidad
     */
    public int estabilidad(Grafo grafo) {
        if (fichas.isEmpty()) return 100;
        int conAlianza = 0;
        int paranoiaTotal = 0;
        for (FichaSobreviviente ficha : fichas.values()) {
            paranoiaTotal += ficha.getParanoia();
            for (Arco arco : grafo.getUsuario(ficha.getId()).getSalientes(TipoRelacion.AMISTAD)) {
                Arco vuelta = grafo.getArco(arco.getDestino(), ficha.getId());
                if (vuelta != null && vuelta.getTipo() == TipoRelacion.AMISTAD) {
                    conAlianza++;
                    break;
                }
            }
        }
        double integracion = (double) conAlianza / fichas.size();
        double calma = 1.0 - (double) paranoiaTotal / (fichas.size() * FichaSobreviviente.PARANOIA_MAXIMA);
        return (int) Math.round(100 * (0.55 * integracion + 0.45 * calma));
    }

    /** @return ID de la líder, o -1 si no hay sobrevivientes */
    public int getIdLider() {
        for (FichaSobreviviente ficha : fichas.values()) {
            if (ficha.getRol() == Rol.LIDER) return ficha.getId();
        }
        return -1;
    }

    /** @return fichas en el orden del grafo */
    public Collection<FichaSobreviviente> getFichas() {
        return Collections.unmodifiableCollection(fichas.values());
    }

    /** @return turno actual (0 antes del primero) */
    public int getTurno() {
        return turno;
    }

    /** @return semilla de la partida */
    public long getSemilla() {
        return semilla;
    }

    /** @return crisis activa, o {@code null} */
    public Crisis getCrisis() {
        return crisis;
    }

    /** @return turnos que lleva la crisis activa sin resolverse */
    public int getTurnosCrisis() {
        return turnosCrisis;
    }

    private FichaSobreviviente obtener(int id) {
        FichaSobreviviente ficha = fichas.get(id);
        if (ficha == null) {
            throw new IllegalArgumentException("No existe el sobreviviente con ID " + id);
        }
        return ficha;
    }

    private static String nombre(Grafo grafo, int id) {
        Usuario usuario = grafo.getUsuario(id);
        return usuario == null ? "#" + id : usuario.getNombre();
    }

    private static String nombres(Grafo grafo, List<Integer> ids) {
        return nombres(grafo, ids, ", ");
    }

    private static String nombres(Grafo grafo, List<Integer> ids, String separador) {
        List<String> lista = new ArrayList<>();
        for (int id : ids) {
            lista.add(nombre(grafo, id));
        }
        return String.join(separador, lista);
    }
}
