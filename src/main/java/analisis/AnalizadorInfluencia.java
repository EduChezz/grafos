package analisis;

import modelo.Grafo;
import modelo.TipoRelacion;
import modelo.Usuario;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * Análisis de influencia: quiénes son los estudiantes más importantes de la red.
 * <ul>
 *   <li><b>Centralidad de grado</b>: qué fracción de la red lo considera amigo.</li>
 *   <li><b>Centralidad de intermediación</b>: en qué fracción de los caminos más cortos entre
 *       otros dos estudiantes aparece. Alguien con intermediación alta es un "puente" entre grupos.</li>
 * </ul>
 * Solo cuenta los arcos del tipo analizado (Amistad por defecto).
 */
public class AnalizadorInfluencia extends AnalizadorBase {
    private static final double EPSILON = 1e-9;

    /**
     * @param grafo grafo a analizar (sigue arcos de Amistad)
     */
    public AnalizadorInfluencia(Grafo grafo) {
        super(grafo);
    }

    /**
     * @param grafo         grafo a analizar
     * @param tipoAnalizado tipo de arco que se sigue
     */
    public AnalizadorInfluencia(Grafo grafo, TipoRelacion tipoAnalizado) {
        super(grafo, tipoAnalizado);
    }

    // Grado

    /**
     * @param id ID del estudiante
     * @return a cuántos considera amigos
     */
    public int gradoSalida(int id) {
        validarUsuario(id);
        return vecinos(id).size();
    }

    /**
     * Se lee directamente de la lista de entrantes del vértice: O(grado), sin recorrer el grafo.
     *
     * @param id ID del estudiante
     * @return cuántos lo consideran amigo
     */
    public int gradoEntrada(int id) {
        validarUsuario(id);
        return vecinosEntrantes(id).size();
    }

    /**
     * @param id ID del estudiante
     * @return grado de entrada / (n − 1), entre 0 y 1
     */
    public double centralidadGrado(int id) {
        int n = grafo.getNumUsuarios();
        if (n < 2) {
            validarUsuario(id);
            return 0.0;
        }
        return (double) gradoEntrada(id) / (n - 1);
    }

    // Intermediación (algoritmo de Brandes)

    /**
     * Centralidad de intermediación con el algoritmo de Brandes, O(V·E).
     * Desde cada origen se hace un BFS que cuenta los caminos más cortos y luego se
     * acumulan las dependencias en orden inverso. Se normaliza dividiendo entre (n−1)(n−2).
     *
     * @return mapa ID → intermediación normalizada
     */
    public Map<Integer, Double> centralidadIntermediacion() {
        List<Usuario> usuarios = grafo.getUsuarios();
        int n = usuarios.size();

        Map<Integer, List<Integer>> adyacencia = new HashMap<>();
        Map<Integer, Double> acumulado = new LinkedHashMap<>();
        for (Usuario usuario : usuarios) {
            adyacencia.put(usuario.getId(), vecinos(usuario.getId()));
            acumulado.put(usuario.getId(), 0.0);
        }

        for (Usuario origen : usuarios) {
            int s = origen.getId();

            Deque<Integer> pila = new ArrayDeque<>();
            Map<Integer, List<Integer>> predecesores = new HashMap<>();
            Map<Integer, Double> caminos = new HashMap<>();
            Map<Integer, Integer> distancia = new HashMap<>();
            Queue<Integer> cola = new LinkedList<>();

            for (Usuario usuario : usuarios) {
                predecesores.put(usuario.getId(), new ArrayList<>());
                caminos.put(usuario.getId(), 0.0);
            }
            caminos.put(s, 1.0);
            distancia.put(s, 0);
            cola.add(s);

            while (!cola.isEmpty()) {
                int v = cola.poll();
                pila.push(v);
                for (int w : adyacencia.get(v)) {
                    if (!distancia.containsKey(w)) {
                        distancia.put(w, distancia.get(v) + 1);
                        cola.add(w);
                    }
                    if (distancia.get(w) == distancia.get(v) + 1) {
                        caminos.put(w, caminos.get(w) + caminos.get(v));
                        predecesores.get(w).add(v);
                    }
                }
            }

            Map<Integer, Double> dependencia = new HashMap<>();
            for (Usuario usuario : usuarios) {
                dependencia.put(usuario.getId(), 0.0);
            }

            while (!pila.isEmpty()) {
                int w = pila.pop();
                for (int v : predecesores.get(w)) {
                    double aporte = caminos.get(v) / caminos.get(w) * (1.0 + dependencia.get(w));
                    dependencia.put(v, dependencia.get(v) + aporte);
                }
                if (w != s) {
                    acumulado.put(w, acumulado.get(w) + dependencia.get(w));
                }
            }
        }

        Map<Integer, Double> resultado = new LinkedHashMap<>();
        double divisor = (n >= 3) ? (double) (n - 1) * (n - 2) : 0.0;
        for (Map.Entry<Integer, Double> entrada : acumulado.entrySet()) {
            double valor = (divisor == 0.0) ? 0.0 : entrada.getValue() / divisor;
            resultado.put(entrada.getKey(), valor);
        }
        return resultado;
    }

    // Ranking

    /**
     * Ordena por intermediación, luego por grado de entrada y luego por ID menor.
     *
     * @return ranking completo
     */
    public List<ResultadoInfluencia> rankingInfluencia() {
        Map<Integer, Double> intermediacion = centralidadIntermediacion();
        List<ResultadoInfluencia> ranking = new ArrayList<>();

        for (Usuario usuario : grafo.getUsuarios()) {
            int id = usuario.getId();
            ranking.add(new ResultadoInfluencia(
                id,
                usuario.getNombre(),
                gradoEntrada(id),
                gradoSalida(id),
                centralidadGrado(id),
                intermediacion.get(id)
            ));
        }

        ranking.sort((x, y) -> {
            double diferencia = y.getCentralidadIntermediacion() - x.getCentralidadIntermediacion();
            if (Math.abs(diferencia) > EPSILON) {
                return diferencia > 0 ? 1 : -1;
            }
            int porEntrada = Integer.compare(y.getGradoEntrada(), x.getGradoEntrada());
            if (porEntrada != 0) return porEntrada;
            return Integer.compare(x.getIdUsuario(), y.getIdUsuario());
        });
        return ranking;
    }

    /**
     * @param n cantidad de puestos
     * @return los primeros {@code n} del ranking
     */
    public List<ResultadoInfluencia> rankingInfluencia(int n) {
        if (n <= 0) {
            throw new IllegalArgumentException("n debe ser mayor que 0");
        }
        List<ResultadoInfluencia> ranking = rankingInfluencia();
        return new ArrayList<>(ranking.subList(0, Math.min(n, ranking.size())));
    }
}