package juego;

import analisis.AnalizadorBase;
import modelo.Arco;
import modelo.Grafo;
import modelo.TipoRelacion;
import modelo.Usuario;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Algoritmos de grafos aplicados al refugio. Solo sigue arcos de confianza (Amistad).
 * <ul>
 *   <li><b>Ruta SOS</b> (BFS): la cadena de confianza más corta desde alguien que pide ayuda
 *       hasta la persona más cercana con el rol necesario.</li>
 *   <li><b>Alerta de motín</b> (DFS, algoritmo de Kosaraju): grupos fuertemente conexos (todos
 *       pueden llegar a todos por confianza) que se cierran sobre sí mismos y no tienen ninguna
 *       cadena de confianza que llegue a la líder.</li>
 * </ul>
 */
public class AnalizadorRefugio extends AnalizadorBase {
    /** Tamaño mínimo para considerar un grupo como posible motín. */
    public static final int TAMANO_MINIMO_MOTIN = 3;
    /** Cerrazón a partir de la cual un grupo es sospechoso. */
    public static final double CERRAZON_SOSPECHOSA = 0.75;

    /**
     * Resultado de la Ruta SOS.
     *
     * @param camino     IDs de quien pide ayuda hasta quien la da; vacío si no hay ruta
     * @param explorados IDs en el orden en que el BFS los revisó
     */
    public record ResultadoRuta(List<Integer> camino, List<Integer> explorados) {
        /** @return {@code true} si se encontró a alguien */
        public boolean encontrada() {
            return !camino.isEmpty();
        }
    }

    /**
     * Grupo cerrado detectado por la alerta de motín.
     *
     * @param miembros      IDs de la componente fuertemente conexa
     * @param cerrazon      fracción de la confianza de sus miembros que queda dentro del grupo (0..1)
     * @param alcanzaLider  {@code true} si alguna cadena de confianza del grupo llega a la líder
     * @param riesgo        ALTO (no llega a la líder), MEDIO (muy cerrado) o BAJO
     */
    public record GrupoCerrado(List<Integer> miembros, double cerrazon, boolean alcanzaLider, String riesgo) {
    }

    /**
     * @param grafo grafo del refugio
     */
    public AnalizadorRefugio(Grafo grafo) {
        super(grafo, TipoRelacion.AMISTAD);
    }

    // Ruta SOS (BFS)

    /**
     * BFS desde {@code origen} que se detiene en el primer estudiante que esté en {@code objetivos}.
     * Como el BFS avanza nivel por nivel, el primero que encuentra es el más cercano en saltos. O(V + E).
     *
     * @param origen    quien pide ayuda
     * @param objetivos IDs que pueden ayudar (por ejemplo, todos los ingenieros)
     * @return camino y orden de exploración
     */
    public ResultadoRuta rutaSOS(int origen, Set<Integer> objetivos) {
        validarUsuario(origen);
        Map<Integer, Integer> padres = new HashMap<>();
        Queue<Integer> cola = new LinkedList<>();
        List<Integer> explorados = new ArrayList<>();

        padres.put(origen, -1);
        cola.add(origen);
        while (!cola.isEmpty()) {
            int actual = cola.poll();
            explorados.add(actual);
            if (actual != origen && objetivos.contains(actual)) {
                return new ResultadoRuta(reconstruir(padres, actual), explorados);
            }
            for (int vecino : vecinos(actual)) {
                if (!padres.containsKey(vecino)) {
                    padres.put(vecino, actual);
                    cola.add(vecino);
                }
            }
        }
        return new ResultadoRuta(new ArrayList<>(), explorados);
    }

    private List<Integer> reconstruir(Map<Integer, Integer> padres, int destino) {
        LinkedList<Integer> camino = new LinkedList<>();
        for (int actual = destino; actual != -1; actual = padres.get(actual)) {
            camino.addFirst(actual);
        }
        return new ArrayList<>(camino);
    }

    // Alerta de motín (DFS, Kosaraju)

    /**
     * Componentes fuertemente conexas con el algoritmo de Kosaraju, que usa dos DFS. O(V + E).
     * <ol>
     *   <li>DFS sobre el grafo y se apila cada vértice cuando <b>termina</b> (orden de finalización).</li>
     *   <li>DFS sobre el grafo <b>transpuesto</b> (siguiendo los arcos entrantes, que cada vértice ya
     *       tiene en su lista), sacando los vértices de la pila. Cada DFS de este paso es una componente.</li>
     * </ol>
     *
     * @return componentes, cada una con sus IDs
     */
    public List<List<Integer>> componentesFuertementeConexas() {
        Deque<Integer> finalizacion = new ArrayDeque<>();
        Set<Integer> visitados = new HashSet<>();
        for (Usuario usuario : grafo.getUsuarios()) {
            if (!visitados.contains(usuario.getId())) {
                dfsFinalizacion(usuario.getId(), visitados, finalizacion);
            }
        }

        List<List<Integer>> componentes = new ArrayList<>();
        Set<Integer> asignados = new HashSet<>();
        while (!finalizacion.isEmpty()) {
            int v = finalizacion.pop();
            if (asignados.contains(v)) continue;
            List<Integer> componente = new ArrayList<>();
            dfsTranspuesto(v, asignados, componente);
            componente.sort(Integer::compare);
            componentes.add(componente);
        }
        return componentes;
    }

    private void dfsFinalizacion(int v, Set<Integer> visitados, Deque<Integer> finalizacion) {
        visitados.add(v);
        for (int w : vecinos(v)) {
            if (!visitados.contains(w)) {
                dfsFinalizacion(w, visitados, finalizacion);
            }
        }
        finalizacion.push(v);
    }

    private void dfsTranspuesto(int v, Set<Integer> asignados, List<Integer> componente) {
        asignados.add(v);
        componente.add(v);
        for (int w : vecinosEntrantes(v)) {
            if (!asignados.contains(w)) {
                dfsTranspuesto(w, asignados, componente);
            }
        }
    }

    /**
     * Estudiantes a los que se puede llegar desde {@code inicio} siguiendo confianza (DFS).
     *
     * @param inicio ID de partida
     * @return conjunto de alcanzables, incluido el inicio
     */
    public Set<Integer> alcanzables(int inicio) {
        validarUsuario(inicio);
        Set<Integer> visitados = new HashSet<>();
        dfsAlcance(inicio, visitados);
        return visitados;
    }

    private void dfsAlcance(int v, Set<Integer> visitados) {
        visitados.add(v);
        for (int w : vecinos(v)) {
            if (!visitados.contains(w)) dfsAlcance(w, visitados);
        }
    }

    /**
     * Busca grupos que podrían amotinarse: componentes fuertemente conexas de al menos
     * {@value #TAMANO_MINIMO_MOTIN} miembros que no incluyen a la líder.
     * <ul>
     *   <li><b>ALTO</b>: ninguna cadena de confianza del grupo llega a la líder.</li>
     *   <li><b>MEDIO</b>: llega, pero el {@value #CERRAZON_SOSPECHOSA} o más de su confianza queda dentro.</li>
     *   <li><b>BAJO</b>: el resto.</li>
     * </ul>
     *
     * @param lider ID de la líder
     * @return grupos ordenados del más riesgoso al menos riesgoso
     */
    public List<GrupoCerrado> alertaMotin(int lider) {
        validarUsuario(lider);
        List<GrupoCerrado> grupos = new ArrayList<>();
        for (List<Integer> componente : componentesFuertementeConexas()) {
            if (componente.size() < TAMANO_MINIMO_MOTIN || componente.contains(lider)) continue;

            Set<Integer> miembros = new HashSet<>(componente);
            int internos = 0;
            int total = 0;
            for (int id : componente) {
                for (Arco arco : arcosSalientes(id)) {
                    total++;
                    if (miembros.contains(arco.getDestino())) internos++;
                }
            }
            double cerrazon = total == 0 ? 0.0 : (double) internos / total;
            // En una componente fuertemente conexa todos alcanzan lo mismo: basta un DFS
            boolean alcanzaLider = alcanzables(componente.get(0)).contains(lider);
            String riesgo = !alcanzaLider ? "ALTO" : cerrazon >= CERRAZON_SOSPECHOSA ? "MEDIO" : "BAJO";
            grupos.add(new GrupoCerrado(componente, cerrazon, alcanzaLider, riesgo));
        }
        grupos.sort((a, b) -> {
            int porRiesgo = Integer.compare(peso(b.riesgo()), peso(a.riesgo()));
            return porRiesgo != 0 ? porRiesgo : Integer.compare(b.miembros().size(), a.miembros().size());
        });
        return grupos;
    }

    private static int peso(String riesgo) {
        switch (riesgo) {
            case "ALTO": return 3;
            case "MEDIO": return 2;
            default: return 1;
        }
    }
}
