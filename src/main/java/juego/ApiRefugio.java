package juego;

import analisis.AnalizadorInfluencia;
import analisis.AnalizadorRed;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import modelo.Grafo;
import modelo.TipoRelacion;
import modelo.Usuario;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Rutas JSON del juego Refugio AP-4. El {@link web.ServidorWeb} le pasa todo lo que empieza con
 * {@code /api/refugio/}, ya dentro de su bloque sincronizado.
 * <pre>
 * GET  /api/refugio/estado                    estado del juego (sin el grafo: ese está en /api/estado)
 * POST /api/refugio/turno                     avanza un turno y devuelve los eventos
 * POST /api/refugio/reiniciar                 {semilla?}  partida nueva sobre el grafo actual
 * PUT  /api/refugio/sobrevivientes/{id}       {rol?, paranoia?}
 * POST /api/refugio/sos                       {origen, rol}   Ruta SOS (BFS)
 * GET  /api/refugio/motin                     Alerta de motín (DFS / Kosaraju)
 * </pre>
 */
public class ApiRefugio {
    private final Refugio refugio = new Refugio();

    /**
     * @param metodo método HTTP
     * @param partes ruta dividida por "/" sin el prefijo {@code /api/} (partes[0] es "refugio")
     * @param q      parámetros de la consulta
     * @param cuerpo cuerpo de la petición
     * @param grafo  grafo actual de la aplicación
     * @return respuesta JSON
     */
    public JsonElement manejar(String metodo, String[] partes, Map<String, String> q, String cuerpo, Grafo grafo) {
        List<Evento> eventos = new ArrayList<>(refugio.sincronizar(grafo));
        String accion = partes.length > 1 ? partes[1] : "";

        switch (accion) {
            case "estado":
                exigir(metodo, "GET");
                return conEventos(estado(grafo), eventos);
            case "turno":
                exigir(metodo, "POST");
                eventos.addAll(refugio.avanzarTurno(grafo));
                return conEventos(estado(grafo), eventos);
            case "reiniciar": {
                exigir(metodo, "POST");
                JsonObject datos = cuerpo == null || cuerpo.isBlank() ? new JsonObject() : objeto(cuerpo);
                long semilla = datos.has("semilla") && !datos.get("semilla").isJsonNull()
                    ? (long) numero(datos, "semilla")
                    : Refugio.SEMILLA_POR_DEFECTO;
                return conEventos(estado(grafo), refugio.reiniciar(grafo, semilla));
            }
            case "sobrevivientes": {
                exigir(metodo, "PUT");
                if (partes.length != 3) throw new IllegalArgumentException("Falta el ID del sobreviviente");
                int id = entero(partes[2], "id");
                JsonObject datos = objeto(cuerpo);
                if (datos.has("rol")) refugio.cambiarRol(id, rol(datos.get("rol").getAsString()));
                if (datos.has("paranoia")) refugio.cambiarParanoia(id, (int) numero(datos, "paranoia"));
                return conEventos(estado(grafo), eventos);
            }
            case "sos": {
                exigir(metodo, "POST");
                JsonObject datos = objeto(cuerpo);
                int origen = (int) numero(datos, "origen");
                Rol rol = rol(datos.get("rol") == null ? "" : datos.get("rol").getAsString());
                AnalizadorRefugio.ResultadoRuta ruta = refugio.rutaSOS(grafo, origen, rol, eventos);
                JsonObject respuesta = conEventos(estado(grafo), eventos);
                respuesta.addProperty("origen", origen);
                respuesta.addProperty("rolBuscado", rol.name());
                respuesta.addProperty("encontrada", ruta.encontrada());
                respuesta.add("camino", lista(ruta.camino()));
                respuesta.add("explorados", lista(ruta.explorados()));
                return respuesta;
            }
            case "motin":
                exigir(metodo, "GET");
                return conEventos(motin(grafo), eventos);
            default:
                throw new IllegalArgumentException("Ruta del refugio no encontrada: " + accion);
        }
    }

    // Respuestas

    private JsonObject estado(Grafo grafo) {
        JsonObject json = new JsonObject();
        json.addProperty("turno", refugio.getTurno());
        json.addProperty("semilla", refugio.getSemilla());
        int estabilidad = refugio.estabilidad(grafo);
        json.addProperty("estabilidad", estabilidad);
        json.addProperty("nivel", estabilidad >= 70 ? "ESTABLE" : estabilidad >= 40 ? "TENSO" : "CRITICO");
        json.addProperty("paranoiaTraicion", Refugio.PARANOIA_TRAICION);

        int lider = refugio.getIdLider();
        json.addProperty("lider", lider);

        Crisis crisis = refugio.getCrisis();
        if (crisis == null) {
            json.add("crisis", JsonNull.INSTANCE);
        } else {
            JsonObject c = new JsonObject();
            c.addProperty("tipo", crisis.name());
            c.addProperty("texto", crisis.getDescripcion());
            c.addProperty("rol", crisis.getRolNecesario().name());
            c.addProperty("turnos", refugio.getTurnosCrisis());
            json.add("crisis", c);
        }

        // Influencia: líder moral (más confianza entrante) y puente (mayor intermediación)
        int liderMoral = -1;
        int mejorEntrada = 0;
        int puente = -1;
        double mejorPuente = 1e-9;
        if (grafo.getNumUsuarios() > 0) {
            AnalizadorInfluencia influencia = new AnalizadorInfluencia(grafo);
            Map<Integer, Double> intermediacion = influencia.centralidadIntermediacion();
            for (Usuario usuario : grafo.getUsuarios()) {
                int id = usuario.getId();
                int entrada = influencia.gradoEntrada(id);
                if (entrada > mejorEntrada) {
                    mejorEntrada = entrada;
                    liderMoral = id;
                }
                if (intermediacion.get(id) > mejorPuente) {
                    mejorPuente = intermediacion.get(id);
                    puente = id;
                }
            }
        }
        json.addProperty("liderMoral", liderMoral);
        json.addProperty("puente", puente);

        JsonArray sobrevivientes = new JsonArray();
        int alianzas = 0;
        for (FichaSobreviviente ficha : refugio.getFichas()) {
            JsonObject s = new JsonObject();
            s.addProperty("id", ficha.getId());
            s.addProperty("rol", ficha.getRol().name());
            s.addProperty("paranoia", ficha.getParanoia());
            s.addProperty("sala", ficha.getSala().name());
            sobrevivientes.add(s);
        }
        for (modelo.Arista arista : grafo.getAristas()) {
            if (arista.esMutua(TipoRelacion.AMISTAD)) alianzas++;
        }
        json.add("sobrevivientes", sobrevivientes);
        json.addProperty("alianzas", alianzas);

        JsonArray roles = new JsonArray();
        for (Rol rol : Rol.values()) {
            JsonObject r = new JsonObject();
            r.addProperty("nombre", rol.name());
            r.addProperty("etiqueta", rol.getEtiqueta());
            r.addProperty("sala", rol.getSalaTrabajo().name());
            roles.add(r);
        }
        json.add("roles", roles);

        JsonArray salas = new JsonArray();
        for (Sala sala : Sala.values()) {
            JsonObject s = new JsonObject();
            s.addProperty("nombre", sala.name());
            s.addProperty("etiqueta", sala.getEtiqueta());
            s.addProperty("piso", sala.getPiso());
            salas.add(s);
        }
        json.add("salas", salas);
        return json;
    }

    private JsonObject motin(Grafo grafo) {
        JsonObject json = new JsonObject();
        int lider = refugio.getIdLider();
        json.addProperty("lider", lider);
        if (lider == -1) {
            json.add("barrido", new JsonArray());
            json.add("noAlcanzados", new JsonArray());
            json.add("componentes", new JsonArray());
            json.add("grupos", new JsonArray());
            return json;
        }

        AnalizadorRefugio analizador = new AnalizadorRefugio(grafo);
        List<Integer> barrido = new AnalizadorRed(grafo).dfs(lider);
        Set<Integer> alcanzados = analizador.alcanzables(lider);
        List<Integer> noAlcanzados = new ArrayList<>();
        for (Usuario usuario : grafo.getUsuarios()) {
            if (!alcanzados.contains(usuario.getId())) noAlcanzados.add(usuario.getId());
        }

        JsonArray componentes = new JsonArray();
        for (List<Integer> componente : analizador.componentesFuertementeConexas()) {
            componentes.add(lista(componente));
        }

        JsonArray grupos = new JsonArray();
        for (AnalizadorRefugio.GrupoCerrado grupo : analizador.alertaMotin(lider)) {
            JsonObject g = new JsonObject();
            g.add("miembros", lista(grupo.miembros()));
            g.addProperty("cerrazon", grupo.cerrazon());
            g.addProperty("alcanzaLider", grupo.alcanzaLider());
            g.addProperty("riesgo", grupo.riesgo());
            grupos.add(g);
        }

        json.add("barrido", lista(barrido));
        json.add("noAlcanzados", lista(noAlcanzados));
        json.add("componentes", componentes);
        json.add("grupos", grupos);
        return json;
    }

    private JsonObject conEventos(JsonObject respuesta, List<Evento> eventos) {
        JsonArray lista = new JsonArray();
        for (Evento evento : eventos) {
            JsonObject e = new JsonObject();
            e.addProperty("tipo", evento.tipo());
            e.addProperty("texto", evento.texto());
            e.add("ids", lista(evento.ids()));
            if (evento.sala() != null) e.addProperty("sala", evento.sala().name());
            lista.add(e);
        }
        respuesta.add("eventos", lista);
        return respuesta;
    }

    // Lectura de datos

    private static void exigir(String metodo, String esperado) {
        if (!esperado.equals(metodo)) {
            throw new IllegalArgumentException("Método no permitido: se esperaba " + esperado);
        }
    }

    private static JsonObject objeto(String cuerpo) {
        if (cuerpo == null || cuerpo.isBlank()) {
            throw new IllegalArgumentException("El cuerpo de la petición está vacío");
        }
        try {
            JsonElement elemento = JsonParser.parseString(cuerpo);
            if (!elemento.isJsonObject()) throw new IllegalArgumentException("El cuerpo debe ser un objeto JSON");
            return elemento.getAsJsonObject();
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("JSON mal formado: " + e.getMessage());
        }
    }

    private static double numero(JsonObject objeto, String campo) {
        JsonElement elemento = objeto.get(campo);
        if (elemento == null || !elemento.isJsonPrimitive() || !elemento.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Falta el campo '" + campo + "' o no es un número");
        }
        return elemento.getAsDouble();
    }

    private static int entero(String texto, String nombre) {
        try {
            return Integer.parseInt(texto.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + nombre + "' debe ser un número entero");
        }
    }

    private static Rol rol(String texto) {
        try {
            return Rol.valueOf(texto.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Rol desconocido: '" + texto + "'");
        }
    }

    private static JsonArray lista(List<Integer> valores) {
        JsonArray lista = new JsonArray();
        for (int valor : valores) {
            lista.add(valor);
        }
        return lista;
    }
}
