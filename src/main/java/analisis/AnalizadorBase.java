package analisis;

import modelo.Arco;
import modelo.Grafo;
import modelo.TipoRelacion;
import modelo.Usuario;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Base común de los analizadores: guarda el grafo y el tipo de relación que se analiza
 * (por defecto {@link TipoRelacion#AMISTAD}) y ofrece consultas de vecinos filtradas por ese tipo.
 */
public abstract class AnalizadorBase {
    /** Grafo sobre el que se trabaja. */
    protected final Grafo grafo;
    /** Solo se siguen los arcos de este tipo. */
    protected final TipoRelacion tipoAnalizado;

    /**
     * Analiza solo los arcos de Amistad.
     *
     * @param grafo grafo a analizar
     */
    protected AnalizadorBase(Grafo grafo) {
        this(grafo, TipoRelacion.AMISTAD);
    }

    /**
     * @param grafo         grafo a analizar
     * @param tipoAnalizado tipo de arco que se sigue
     */
    protected AnalizadorBase(Grafo grafo, TipoRelacion tipoAnalizado) {
        this.grafo = Objects.requireNonNull(grafo, "El grafo no puede ser null");
        this.tipoAnalizado = Objects.requireNonNull(tipoAnalizado, "El tipo no puede ser null");
    }

    /** @return tipo de arco que se sigue */
    public TipoRelacion getTipoAnalizado() {
        return tipoAnalizado;
    }

    /**
     * @param id ID del estudiante
     * @return arcos salientes del tipo analizado (vacío si el estudiante no existe)
     */
    protected List<Arco> arcosSalientes(int id) {
        Usuario usuario = grafo.getUsuario(id);
        return usuario == null ? new ArrayList<>() : usuario.getSalientes(tipoAnalizado);
    }

    /**
     * Vecinos de salida: a quiénes considera amigos {@code id}.
     *
     * @param id ID del estudiante
     * @return IDs de destino de sus arcos del tipo analizado
     */
    protected List<Integer> vecinos(int id) {
        List<Integer> resultado = new ArrayList<>();
        for (Arco arco : arcosSalientes(id)) {
            resultado.add(arco.getDestino());
        }
        return resultado;
    }

    /**
     * Vecinos de entrada: quiénes consideran amigo a {@code id}.
     * Se lee directamente de la lista de entrantes del vértice.
     *
     * @param id ID del estudiante
     * @return IDs de origen de sus arcos entrantes del tipo analizado
     */
    protected List<Integer> vecinosEntrantes(int id) {
        List<Integer> resultado = new ArrayList<>();
        Usuario usuario = grafo.getUsuario(id);
        if (usuario == null) return resultado;
        for (Arco arco : usuario.getEntrantes(tipoAnalizado)) {
            resultado.add(arco.getOrigen());
        }
        return resultado;
    }

    /**
     * Vecinos mutuos: la relación es del tipo analizado en los dos sentidos.
     *
     * @param id ID del estudiante
     * @return IDs con relación mutua
     */
    protected List<Integer> vecinosMutuos(int id) {
        List<Integer> resultado = new ArrayList<>();
        Usuario usuario = grafo.getUsuario(id);
        if (usuario == null) return resultado;
        for (Arco arco : arcosSalientes(id)) {
            Arco inverso = usuario.getArcoDesde(arco.getDestino());
            if (inverso != null && inverso.getTipo() == tipoAnalizado) {
                resultado.add(arco.getDestino());
            }
        }
        return resultado;
    }

    /**
     * @param id ID a comprobar
     * @throws IllegalArgumentException si no existe
     */
    protected void validarUsuario(int id) {
        if (!grafo.existeUsuario(id)) {
            throw new IllegalArgumentException("No existe el usuario con ID " + id);
        }
    }
}
