package analisis;

import modelo.Arco;
import modelo.Grafo;
import modelo.TipoRelacion;
import modelo.Usuario;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Recorridos y análisis de la red: BFS, DFS, caminos, amigos en común, sugerencias y comunidades.
 * <p>
 * Solo sigue los arcos del tipo analizado (Amistad por defecto) y respeta su sentido:
 * que A considere amigo a B no implica que B considere amigo a A.
 */
public class AnalizadorRed extends AnalizadorBase {
    private static final double EPSILON = 1e-9;

    /**
     * @param grafo grafo a analizar (sigue arcos de Amistad)
     */
    public AnalizadorRed(Grafo grafo) {
        super(grafo);
    }

    /**
     * @param grafo         grafo a analizar
     * @param tipoAnalizado tipo de arco que se sigue
     */
    public AnalizadorRed(Grafo grafo, TipoRelacion tipoAnalizado) {
        super(grafo, tipoAnalizado);
    }

    // Recorridos

    /**
     * Búsqueda a lo ancho: visita primero todos los vecinos directos, luego los vecinos de
     * esos vecinos, y así nivel por nivel. Usa una cola (FIFO). O(V + E).
     *
     * @param inicio ID del estudiante de partida
     * @return IDs en el orden en que se visitaron
     */
    public List<Integer> bfs(int inicio) {
        validarUsuario(inicio);
        List<Integer> orden = new ArrayList<>();
        Set<Integer> visitados = new HashSet<>();
        Queue<Integer> cola = new LinkedList<>();

        visitados.add(inicio);
        cola.add(inicio);
        while (!cola.isEmpty()) {
            int actual = cola.poll();
            orden.add(actual);
            for (int vecino : vecinos(actual)) {
                if (visitados.add(vecino)) {
                    cola.add(vecino);
                }
            }
        }
        return orden;
    }

    /**
     * Búsqueda en profundidad: sigue un camino lo más lejos posible y deja los demás vecinos
     * pendientes en una pila (LIFO) para revisarlos después. O(V + E).
     *
     * @param inicio ID del estudiante de partida
     * @return IDs en el orden en que se visitaron
     */
    public List<Integer> dfs(int inicio) {
        validarUsuario(inicio);
        List<Integer> orden = new ArrayList<>();
        Set<Integer> visitados = new HashSet<>();
        Deque<Integer> pila = new LinkedList<>();

        pila.push(inicio);
        while (!pila.isEmpty()) {
            int actual = pila.pop();
            if (!visitados.add(actual)) continue;
            orden.add(actual);

            // Se apilan al revés para visitar los vecinos en su orden original
            List<Integer> vecinos = vecinos(actual);
            for (int i = vecinos.size() - 1; i >= 0; i--) {
                int vecino = vecinos.get(i);
                if (!visitados.contains(vecino)) {
                    pila.push(vecino);
                }
            }
        }
        return orden;
    }

    /**
     * Camino con la menor cantidad de saltos, usando BFS. O(V + E).
     *
     * @param origen  ID de partida
     * @param destino ID de llegada
     * @return IDs del camino, de origen a destino; vacío si no hay camino
     */
    public List<Integer> caminoMasCorto(int origen, int destino) {
        validarUsuario(origen);
        validarUsuario(destino);

        if (origen == destino) {
            List<Integer> propio = new ArrayList<>();
            propio.add(origen);
            return propio;
        }

        Map<Integer, Integer> padres = new HashMap<>();
        Queue<Integer> cola = new LinkedList<>();
        padres.put(origen, -1);
        cola.add(origen);

        while (!cola.isEmpty()) {
            int actual = cola.poll();
            for (int vecino : vecinos(actual)) {
                if (padres.containsKey(vecino)) continue;
                padres.put(vecino, actual);
                if (vecino == destino) {
                    return reconstruirCamino(padres, destino);
                }
                cola.add(vecino);
            }
        }
        return new ArrayList<>();
    }

    /**
     * Camino de <b>mayor afinidad</b> usando el algoritmo de Dijkstra con cola de prioridad.
     * <p>
     * El peso de un arco es la afinidad (más alto = relación más cercana), así que el costo de
     * pasar por un arco es {@code 1 / peso}: las amistades fuertes "acercan" más. El resultado
     * prefiere ir por amigos muy cercanos aunque haya que dar más saltos. O((V + E) log V).
     *
     * @param origen  ID de partida
     * @param destino ID de llegada
     * @return IDs del camino, de origen a destino; vacío si no hay camino
     */
    public List<Integer> caminoMayorAfinidad(int origen, int destino) {
        validarUsuario(origen);
        validarUsuario(destino);

        Map<Integer, Double> distancia = new HashMap<>();
        Map<Integer, Integer> padres = new HashMap<>();
        PriorityQueue<double[]> cola = new PriorityQueue<>(Comparator.comparingDouble(e -> e[0]));

        distancia.put(origen, 0.0);
        padres.put(origen, -1);
        cola.add(new double[]{0.0, origen});

        while (!cola.isEmpty()) {
            double[] entrada = cola.poll();
            int actual = (int) entrada[1];
            if (entrada[0] > distancia.get(actual) + EPSILON) continue;
            if (actual == destino) {
                return reconstruirCamino(padres, destino);
            }
            for (Arco arco : arcosSalientes(actual)) {
                int vecino = arco.getDestino();
                double nueva = entrada[0] + costo(arco);
                Double previa = distancia.get(vecino);
                if (previa == null || nueva < previa - EPSILON) {
                    distancia.put(vecino, nueva);
                    padres.put(vecino, actual);
                    cola.add(new double[]{nueva, vecino});
                }
            }
        }
        return new ArrayList<>();
    }

    /**
     * @param arco arco a recorrer
     * @return costo de recorrerlo en el camino de mayor afinidad: {@code 1 / peso}
     */
    public static double costo(Arco arco) {
        return 1.0 / arco.getPeso();
    }

    /**
     * @param camino IDs consecutivos unidos por arcos
     * @return suma de {@code 1 / peso} de los arcos del camino
     * @throws IllegalArgumentException si dos IDs consecutivos no tienen arco
     */
    public double costoCamino(List<Integer> camino) {
        double total = 0.0;
        for (int i = 1; i < camino.size(); i++) {
            Arco arco = grafo.getArco(camino.get(i - 1), camino.get(i));
            if (arco == null) {
                throw new IllegalArgumentException("No hay arco " + camino.get(i - 1) + " -> " + camino.get(i));
            }
            total += costo(arco);
        }
        return total;
    }

    private List<Integer> reconstruirCamino(Map<Integer, Integer> padres, int destino) {
        LinkedList<Integer> camino = new LinkedList<>();
        int actual = destino;
        while (actual != -1) {
            camino.addFirst(actual);
            actual = padres.get(actual);
        }
        return new ArrayList<>(camino);
    }

    // Amigos en común y sugerencias

    /**
     * Estudiantes a los que {@code a} y {@code b} consideran amigos.
     *
     * @param a ID del primer estudiante
     * @param b ID del segundo estudiante
     * @return IDs en común
     */
    public List<Integer> amigosEnComun(int a, int b) {
        validarUsuario(a);
        validarUsuario(b);
        if (a == b) {
            throw new IllegalArgumentException("Elige dos usuarios distintos");
        }

        Set<Integer> amigosB = new HashSet<>(vecinos(b));
        List<Integer> comunes = new ArrayList<>();
        for (int amigo : vecinos(a)) {
            if (amigosB.contains(amigo)) {
                comunes.add(amigo);
            }
        }
        return comunes;
    }

    /**
     * Sugiere amigos de amigos con los que el estudiante todavía no tiene una opinión definida
     * (no hay arco, o el arco es Desconocido).
     *
     * @param id ID del estudiante
     * @return mapa candidato → cantidad de amigos en común, de mayor a menor
     */
    public Map<Integer, Integer> sugerirAmigos(int id) {
        validarUsuario(id);
        Map<Integer, Integer> conteo = new HashMap<>();

        for (int amigo : vecinos(id)) {
            for (int candidato : vecinos(amigo)) {
                if (candidato == id) continue;
                Arco existente = grafo.getArco(id, candidato);
                if (existente != null && existente.getTipo() != TipoRelacion.DESCONOCIDO) continue;
                conteo.merge(candidato, 1, Integer::sum);
            }
        }

        List<Map.Entry<Integer, Integer>> entradas = new ArrayList<>(conteo.entrySet());
        entradas.sort((x, y) -> {
            int porConteo = Integer.compare(y.getValue(), x.getValue());
            return porConteo != 0 ? porConteo : Integer.compare(x.getKey(), y.getKey());
        });

        Map<Integer, Integer> resultado = new LinkedHashMap<>();
        for (Map.Entry<Integer, Integer> entrada : entradas) {
            resultado.put(entrada.getKey(), entrada.getValue());
        }
        return resultado;
    }

    // Comunidades

    /**
     * Detecta comunidades y mide su fuerza.
     * <ol>
     *   <li>Se toma el grafo de <b>amistad mutua</b> (los dos sentidos son Amistad).</li>
     *   <li>Se buscan todos los <b>triángulos</b>: 3 estudiantes amigos entre sí.</li>
     *   <li>Dos triángulos que comparten una pareja pertenecen a la misma comunidad; se agrupan
     *       con un BFS sobre los triángulos (percolación de cliques con k = 3).</li>
     *   <li>Para cada comunidad se calcula la <b>densidad</b>, el <b>núcleo</b> (clique máxima,
     *       con Bron–Kerbosch) y la afinidad promedio.</li>
     * </ol>
     *
     * @return comunidades de mayor a menor tamaño
     */
    public List<Comunidad> detectarComunidades() {
        Map<Integer, Set<Integer>> amigos = new TreeMap<>();
        for (Usuario usuario : grafo.getUsuarios()) {
            amigos.put(usuario.getId(), new TreeSet<>(vecinosMutuos(usuario.getId())));
        }

        List<int[]> triangulos = encontrarTriangulos(amigos);
        Map<String, List<Integer>> triangulosPorPar = indexarPares(triangulos);
        List<Grupo> grupos = agruparTriangulos(triangulos, triangulosPorPar);

        grupos.sort((x, y) -> {
            int porTamano = Integer.compare(y.miembros.size(), x.miembros.size());
            return porTamano != 0 ? porTamano : Integer.compare(x.miembros.first(), y.miembros.first());
        });

        List<Comunidad> comunidades = new ArrayList<>();
        int numero = 1;
        for (Grupo grupo : grupos) {
            List<Integer> miembros = new ArrayList<>(grupo.miembros);
            comunidades.add(new Comunidad(
                numero++,
                miembros,
                grupo.triangulos,
                contarConexiones(miembros, amigos),
                cliqueMaxima(grupo.miembros, amigos),
                afinidadPromedio(miembros, amigos)
            ));
        }
        return comunidades;
    }

    private List<int[]> encontrarTriangulos(Map<Integer, Set<Integer>> amigos) {
        List<int[]> triangulos = new ArrayList<>();
        for (Map.Entry<Integer, Set<Integer>> entrada : amigos.entrySet()) {
            int u = entrada.getKey();
            Set<Integer> amigosU = entrada.getValue();
            for (int v : amigosU) {
                if (v <= u) continue;
                for (int w : amigos.get(v)) {
                    if (w <= v) continue;
                    if (amigosU.contains(w)) {
                        triangulos.add(new int[]{u, v, w});
                    }
                }
            }
        }
        return triangulos;
    }

    private Map<String, List<Integer>> indexarPares(List<int[]> triangulos) {
        Map<String, List<Integer>> pares = new HashMap<>();
        for (int i = 0; i < triangulos.size(); i++) {
            for (String clave : clavesDePares(triangulos.get(i))) {
                pares.computeIfAbsent(clave, k -> new ArrayList<>()).add(i);
            }
        }
        return pares;
    }

    private List<Grupo> agruparTriangulos(List<int[]> triangulos, Map<String, List<Integer>> triangulosPorPar) {
        boolean[] visitado = new boolean[triangulos.size()];
        List<Grupo> grupos = new ArrayList<>();

        for (int i = 0; i < triangulos.size(); i++) {
            if (visitado[i]) continue;

            Grupo grupo = new Grupo();
            Queue<Integer> cola = new LinkedList<>();
            visitado[i] = true;
            cola.add(i);

            while (!cola.isEmpty()) {
                int actual = cola.poll();
                int[] triangulo = triangulos.get(actual);
                grupo.triangulos.add(triangulo);
                for (int miembro : triangulo) {
                    grupo.miembros.add(miembro);
                }
                for (String clave : clavesDePares(triangulo)) {
                    for (int vecino : triangulosPorPar.get(clave)) {
                        if (!visitado[vecino]) {
                            visitado[vecino] = true;
                            cola.add(vecino);
                        }
                    }
                }
            }
            grupos.add(grupo);
        }
        return grupos;
    }

    private String[] clavesDePares(int[] t) {
        return new String[]{
            t[0] + "-" + t[1],
            t[0] + "-" + t[2],
            t[1] + "-" + t[2]
        };
    }

    /** Parejas de miembros con amistad mutua. */
    private int contarConexiones(List<Integer> miembros, Map<Integer, Set<Integer>> amigos) {
        int conexiones = 0;
        for (int i = 0; i < miembros.size(); i++) {
            for (int j = i + 1; j < miembros.size(); j++) {
                if (amigos.get(miembros.get(i)).contains(miembros.get(j))) conexiones++;
            }
        }
        return conexiones;
    }

    /** Promedio del peso de los arcos de amistad mutua entre miembros. */
    private double afinidadPromedio(List<Integer> miembros, Map<Integer, Set<Integer>> amigos) {
        double suma = 0.0;
        int arcos = 0;
        for (int u : miembros) {
            for (int v : miembros) {
                if (u != v && amigos.get(u).contains(v)) {
                    suma += grafo.getArco(u, v).getPeso();
                    arcos++;
                }
            }
        }
        return arcos == 0 ? 0.0 : suma / arcos;
    }

    /**
     * Clique más grande (todos amigos de todos) dentro de un grupo, con Bron–Kerbosch con pivote.
     * Si hay empate, gana la que tiene IDs menores.
     */
    private List<Integer> cliqueMaxima(Set<Integer> miembros, Map<Integer, Set<Integer>> amigos) {
        Map<Integer, Set<Integer>> adyacencia = new HashMap<>();
        for (int u : miembros) {
            Set<Integer> internos = new TreeSet<>(amigos.get(u));
            internos.retainAll(miembros);
            adyacencia.put(u, internos);
        }
        List<List<Integer>> mejor = new ArrayList<>();
        mejor.add(new ArrayList<>());
        bronKerbosch(new TreeSet<>(), new TreeSet<>(miembros), new TreeSet<>(), adyacencia, mejor);
        return mejor.get(0);
    }

    private void bronKerbosch(TreeSet<Integer> r, TreeSet<Integer> p, TreeSet<Integer> x,
                              Map<Integer, Set<Integer>> ady, List<List<Integer>> mejor) {
        if (p.isEmpty() && x.isEmpty()) {
            List<Integer> actual = mejor.get(0);
            if (r.size() > actual.size() || (r.size() == actual.size() && esMenor(r, actual))) {
                mejor.set(0, new ArrayList<>(r));
            }
            return;
        }
        if (r.size() + p.size() < mejor.get(0).size()) return;

        // Pivote: el vértice de P ∪ X con más vecinos en P, para podar ramas
        int pivote = -1;
        int maxVecinos = -1;
        for (Set<Integer> conjunto : List.of(p, x)) {
            for (int u : conjunto) {
                int enP = 0;
                for (int v : ady.get(u)) {
                    if (p.contains(v)) enP++;
                }
                if (enP > maxVecinos) {
                    maxVecinos = enP;
                    pivote = u;
                }
            }
        }

        for (int v : new ArrayList<>(p)) {
            if (ady.get(pivote).contains(v)) continue;
            TreeSet<Integer> nuevoR = new TreeSet<>(r);
            nuevoR.add(v);
            TreeSet<Integer> nuevoP = new TreeSet<>(p);
            nuevoP.retainAll(ady.get(v));
            TreeSet<Integer> nuevoX = new TreeSet<>(x);
            nuevoX.retainAll(ady.get(v));
            bronKerbosch(nuevoR, nuevoP, nuevoX, ady, mejor);
            p.remove(v);
            x.add(v);
        }
    }

    private boolean esMenor(Set<Integer> candidata, List<Integer> actual) {
        if (actual.isEmpty()) return true;
        List<Integer> lista = new ArrayList<>(candidata);
        for (int i = 0; i < lista.size(); i++) {
            int comparacion = Integer.compare(lista.get(i), actual.get(i));
            if (comparacion != 0) return comparacion < 0;
        }
        return false;
    }

    /** Agrupación temporal de triángulos durante la detección. */
    private static class Grupo {
        final TreeSet<Integer> miembros = new TreeSet<>();
        final ArrayList<int[]> triangulos = new ArrayList<>();
    }
}
