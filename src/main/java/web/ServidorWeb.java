package web;

import analisis.AnalizadorInfluencia;
import analisis.AnalizadorRed;
import analisis.Comunidad;
import analisis.ResultadoInfluencia;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import datos.CargadorJson;
import juego.ApiRefugio;
import modelo.Arista;
import modelo.Arco;
import modelo.Grafo;
import modelo.PerfilEstudiante;
import modelo.TipoRelacion;
import modelo.Usuario;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Servidor HTTP local (sin dependencias, con {@code com.sun.net.httpserver}) que sirve la interfaz
 * gráfica y expone el grafo como una API JSON.
 * <p>
 * Java hace todos los cálculos; la página web solo dibuja. Todas las operaciones sobre el grafo se
 * ejecutan dentro de un {@code synchronized} para que las peticiones no se pisen entre sí.
 * <p>
 * Rutas:
 * <pre>
 * GET    /                         interfaz gráfica
 * GET    /api/estado               usuarios, relaciones y tipos
 * POST   /api/usuarios             {nombre, carrera?, semestre?, edad?, correo?}
 * PUT    /api/usuarios/{id}        {carrera?, semestre?, edad?, correo?}  (reemplaza el perfil)
 * DELETE /api/usuarios/{id}
 * POST   /api/relaciones           {origen, destino, tipo, peso?}
 * PUT    /api/relaciones           {origen, destino, tipo}
 * PUT    /api/relaciones/peso      {origen, destino, peso}
 * DELETE /api/relaciones?a=&amp;b=
 * GET    /api/bfs?inicio=          GET /api/dfs?inicio=
 * GET    /api/camino?origen=&amp;destino=          (BFS, menos saltos)
 * GET    /api/camino-afin?origen=&amp;destino=     (Dijkstra, mayor afinidad)
 * GET    /api/en-comun?a=&amp;b=      GET /api/sugerencias?id=
 * GET    /api/comunidades         GET /api/influencia?n=
 * POST   /api/cargar              (cuerpo: JSON del grafo)
 * GET    /api/ejemplos/{nombre}
 * GET    /refugio                  juego Refugio AP-4 (sus rutas /api/refugio/... están en {@link ApiRefugio})
 * </pre>
 */
public class ServidorWeb {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Pattern NOMBRE_EJEMPLO = Pattern.compile("[A-Za-z0-9_-]+");

    private final int puerto;
    private final CargadorJson cargador = new CargadorJson();
    private final Object candado = new Object();
    /** Juego Refugio AP-4: se monta sobre el mismo grafo. */
    private final ApiRefugio refugio = new ApiRefugio();
    private Grafo grafo;
    private HttpServer servidor;

    /**
     * @param grafo  grafo inicial
     * @param puerto puerto local donde escuchar
     */
    public ServidorWeb(Grafo grafo, int puerto) {
        this.grafo = grafo;
        this.puerto = puerto;
    }

    /**
     * Empieza a escuchar en {@code 127.0.0.1:puerto}.
     *
     * @throws IOException si el puerto está ocupado
     */
    public void iniciar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), puerto), 0);
        servidor.createContext("/", this::atender);
        servidor.setExecutor(null);
        servidor.start();
    }

    /** Detiene el servidor si está activo. */
    public void detener() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    /** @return el grafo actual */
    public Grafo getGrafo() {
        synchronized (candado) {
            return grafo;
        }
    }

    /**
     * @param nuevo grafo que reemplaza al actual (por ejemplo, tras cargar un JSON)
     */
    public void reemplazarGrafo(Grafo nuevo) {
        if (nuevo == null) {
            throw new IllegalArgumentException("El grafo no puede ser null");
        }
        synchronized (candado) {
            this.grafo = nuevo;
        }
    }

    // Atención de peticiones

    private void atender(HttpExchange ex) throws IOException {
        try {
            String metodo = ex.getRequestMethod();
            String ruta = ex.getRequestURI().getPath();

            if (ruta.equals("/") || ruta.equals("/index.html")) {
                servirHtml(ex);
            } else if (ruta.equals("/refugio") || ruta.equals("/refugio.html")) {
                servirPagina(ex, "/web/refugio.html");
            } else if (ruta.startsWith("/api/")) {
                Map<String, String> consulta = parsearConsulta(ex.getRequestURI().getRawQuery());
                String cuerpo = leerCuerpo(ex);
                JsonElement respuesta;
                synchronized (candado) {
                    respuesta = manejarApi(metodo, ruta, consulta, cuerpo);
                }
                responder(ex, 200, "application/json; charset=utf-8", GSON.toJson(respuesta));
            } else {
                responderError(ex, 404, "Ruta no encontrada");
            }
        } catch (ErrorHttp e) {
            responderError(ex, e.codigo, e.getMessage());
        } catch (IllegalArgumentException e) {
            responderError(ex, 400, e.getMessage());
        } catch (Exception e) {
            responderError(ex, 500, "Error interno: " + e);
        } finally {
            ex.close();
        }
    }

    private JsonElement manejarApi(String metodo, String ruta, Map<String, String> q, String cuerpo) {
        String[] partes = ruta.substring("/api/".length()).split("/");

        switch (partes[0]) {
            case "estado":
                exigir(metodo, "GET");
                return estado();
            case "usuarios":
                return manejarUsuarios(metodo, partes, cuerpo);
            case "relaciones":
                return manejarRelaciones(metodo, partes, q, cuerpo);
            case "bfs":
                exigir(metodo, "GET");
                return orden(parametroEntero(q, "inicio"), new AnalizadorRed(grafo).bfs(parametroEntero(q, "inicio")));
            case "dfs":
                exigir(metodo, "GET");
                return orden(parametroEntero(q, "inicio"), new AnalizadorRed(grafo).dfs(parametroEntero(q, "inicio")));
            case "camino":
                exigir(metodo, "GET");
                return camino(parametroEntero(q, "origen"), parametroEntero(q, "destino"));
            case "camino-afin":
                exigir(metodo, "GET");
                return caminoAfin(parametroEntero(q, "origen"), parametroEntero(q, "destino"));
            case "en-comun":
                exigir(metodo, "GET");
                return enComun(parametroEntero(q, "a"), parametroEntero(q, "b"));
            case "sugerencias":
                exigir(metodo, "GET");
                return sugerencias(parametroEntero(q, "id"));
            case "comunidades":
                exigir(metodo, "GET");
                return comunidades();
            case "influencia":
                exigir(metodo, "GET");
                return influencia(q);
            case "cargar":
                exigir(metodo, "POST");
                grafo = cargador.cargarDesdeTexto(cuerpo);
                return estado();
            case "ejemplos":
                exigir(metodo, "GET");
                return ejemplo(partes);
            case "refugio":
                return refugio.manejar(metodo, partes, q, cuerpo, grafo);
            default:
                throw new ErrorHttp(404, "Ruta no encontrada");
        }
    }

    // Usuarios y relaciones

    private JsonElement manejarUsuarios(String metodo, String[] partes, String cuerpo) {
        if (partes.length == 1) {
            exigir(metodo, "POST");
            JsonObject datos = cuerpoComoObjeto(cuerpo);
            String nombre = textoDe(datos, "nombre");
            Usuario creado = grafo.agregarUsuario(nombre, perfilDe(datos));
            JsonObject respuesta = estado();
            respuesta.addProperty("idCreado", creado.getId());
            return respuesta;
        }
        if (partes.length == 2) {
            int id = aEntero(partes[1], "id");
            if ("PUT".equals(metodo)) {
                grafo.actualizarPerfil(id, perfilDe(cuerpoComoObjeto(cuerpo)));
                return estado();
            }
            exigir(metodo, "DELETE");
            grafo.eliminarUsuario(id);
            return estado();
        }
        throw new ErrorHttp(404, "Ruta no encontrada");
    }

    private JsonElement manejarRelaciones(String metodo, String[] partes, Map<String, String> q, String cuerpo) {
        if (partes.length == 2 && partes[1].equals("peso")) {
            exigir(metodo, "PUT");
            JsonObject datos = cuerpoComoObjeto(cuerpo);
            grafo.cambiarPeso(enteroDe(datos, "origen"), enteroDe(datos, "destino"), numeroDe(datos, "peso"));
            return estado();
        }
        if (partes.length != 1) {
            throw new ErrorHttp(404, "Ruta no encontrada");
        }
        switch (metodo) {
            case "POST": {
                JsonObject datos = cuerpoComoObjeto(cuerpo);
                TipoRelacion tipo = tipoDe(datos, "tipo");
                double peso = datos.has("peso") && !datos.get("peso").isJsonNull()
                    ? numeroDe(datos, "peso")
                    : tipo.getPesoPorDefecto();
                grafo.agregarRelacion(enteroDe(datos, "origen"), enteroDe(datos, "destino"), tipo, peso);
                return estado();
            }
            case "PUT": {
                JsonObject datos = cuerpoComoObjeto(cuerpo);
                grafo.cambiarTipoRelacion(enteroDe(datos, "origen"), enteroDe(datos, "destino"), tipoDe(datos, "tipo"));
                return estado();
            }
            case "DELETE":
                grafo.eliminarRelacion(parametroEntero(q, "a"), parametroEntero(q, "b"));
                return estado();
            default:
                throw new ErrorHttp(405, "Método no permitido");
        }
    }

    // Análisis

    private JsonObject orden(int inicio, List<Integer> orden) {
        JsonObject respuesta = new JsonObject();
        respuesta.addProperty("inicio", inicio);
        respuesta.add("orden", listaEnteros(orden));
        return respuesta;
    }

    private JsonObject camino(int origen, int destino) {
        List<Integer> camino = new AnalizadorRed(grafo).caminoMasCorto(origen, destino);
        JsonObject respuesta = new JsonObject();
        respuesta.addProperty("origen", origen);
        respuesta.addProperty("destino", destino);
        respuesta.addProperty("encontrado", !camino.isEmpty());
        respuesta.add("camino", listaEnteros(camino));
        if (!camino.isEmpty()) {
            respuesta.addProperty("pasos", camino.size() - 1);
        }
        return respuesta;
    }

    private JsonObject caminoAfin(int origen, int destino) {
        AnalizadorRed analizador = new AnalizadorRed(grafo);
        List<Integer> camino = analizador.caminoMayorAfinidad(origen, destino);
        JsonObject respuesta = new JsonObject();
        respuesta.addProperty("origen", origen);
        respuesta.addProperty("destino", destino);
        respuesta.addProperty("encontrado", !camino.isEmpty());
        respuesta.add("camino", listaEnteros(camino));
        if (!camino.isEmpty()) {
            JsonArray pesos = new JsonArray();
            for (int i = 1; i < camino.size(); i++) {
                pesos.add(grafo.getArco(camino.get(i - 1), camino.get(i)).getPeso());
            }
            respuesta.addProperty("pasos", camino.size() - 1);
            respuesta.addProperty("costo", analizador.costoCamino(camino));
            respuesta.add("pesos", pesos);
        }
        return respuesta;
    }

    private JsonObject enComun(int a, int b) {
        JsonObject respuesta = new JsonObject();
        respuesta.addProperty("a", a);
        respuesta.addProperty("b", b);
        respuesta.add("comunes", listaEnteros(new AnalizadorRed(grafo).amigosEnComun(a, b)));
        return respuesta;
    }

    private JsonObject sugerencias(int id) {
        JsonArray lista = new JsonArray();
        for (Map.Entry<Integer, Integer> entrada : new AnalizadorRed(grafo).sugerirAmigos(id).entrySet()) {
            JsonObject sugerencia = new JsonObject();
            sugerencia.addProperty("id", entrada.getKey());
            sugerencia.addProperty("enComun", entrada.getValue());
            lista.add(sugerencia);
        }
        JsonObject respuesta = new JsonObject();
        respuesta.addProperty("id", id);
        respuesta.add("sugerencias", lista);
        return respuesta;
    }

    private JsonObject comunidades() {
        JsonArray lista = new JsonArray();
        for (Comunidad comunidad : new AnalizadorRed(grafo).detectarComunidades()) {
            JsonArray triangulos = new JsonArray();
            for (int[] triangulo : comunidad.getTriangulos()) {
                JsonArray t = new JsonArray();
                for (int idUsuario : triangulo) {
                    t.add(idUsuario);
                }
                triangulos.add(t);
            }
            JsonObject json = new JsonObject();
            json.addProperty("id", comunidad.getId());
            json.add("miembros", listaEnteros(comunidad.getMiembros()));
            json.add("triangulos", triangulos);
            json.addProperty("conexiones", comunidad.getConexiones());
            json.addProperty("posibles", comunidad.getConexionesPosibles());
            json.addProperty("densidad", comunidad.getDensidad());
            json.addProperty("fuerza", comunidad.getFuerza().name());
            json.addProperty("fuerzaEtiqueta", comunidad.getFuerza().getEtiqueta());
            json.add("nucleo", listaEnteros(comunidad.getNucleo()));
            json.addProperty("afinidadPromedio", comunidad.getAfinidadPromedio());
            lista.add(json);
        }
        JsonObject respuesta = new JsonObject();
        respuesta.add("comunidades", lista);
        return respuesta;
    }

    private JsonObject influencia(Map<String, String> q) {
        AnalizadorInfluencia analizador = new AnalizadorInfluencia(grafo);
        List<ResultadoInfluencia> ranking = q.containsKey("n")
            ? analizador.rankingInfluencia(parametroEntero(q, "n"))
            : analizador.rankingInfluencia();

        JsonArray lista = new JsonArray();
        for (ResultadoInfluencia r : ranking) {
            JsonObject json = new JsonObject();
            json.addProperty("id", r.getIdUsuario());
            json.addProperty("nombre", r.getNombre());
            json.addProperty("gradoEntrada", r.getGradoEntrada());
            json.addProperty("gradoSalida", r.getGradoSalida());
            json.addProperty("centralidadGrado", r.getCentralidadGrado());
            json.addProperty("centralidadIntermediacion", r.getCentralidadIntermediacion());
            lista.add(json);
        }
        JsonObject respuesta = new JsonObject();
        respuesta.add("ranking", lista);
        return respuesta;
    }

    // Estado y ejemplos

    private JsonObject estado() {
        JsonArray usuarios = new JsonArray();
        for (Usuario usuario : grafo.getUsuarios()) {
            JsonObject json = new JsonObject();
            json.addProperty("id", usuario.getId());
            json.addProperty("nombre", usuario.getNombre());
            PerfilEstudiante perfil = usuario.getPerfil();
            json.addProperty("carrera", perfil.carrera());
            json.addProperty("semestre", perfil.semestre());
            json.addProperty("edad", perfil.edad());
            json.addProperty("correo", perfil.correo());
            json.addProperty("gradoSalida", usuario.getSalientes().size());
            json.addProperty("gradoEntrada", usuario.getEntrantes().size());
            usuarios.add(json);
        }

        JsonArray relaciones = new JsonArray();
        for (Arista arista : grafo.getAristas()) {
            JsonObject json = new JsonObject();
            json.addProperty("a", arista.getUsuarioA());
            json.addProperty("b", arista.getUsuarioB());
            json.addProperty("tipoAB", arista.getArcoAB().getTipo().name());
            json.addProperty("tipoBA", arista.getArcoBA().getTipo().name());
            json.addProperty("pesoAB", arista.getArcoAB().getPeso());
            json.addProperty("pesoBA", arista.getArcoBA().getPeso());
            relaciones.add(json);
        }

        JsonArray tipos = new JsonArray();
        for (TipoRelacion tipo : TipoRelacion.values()) {
            JsonObject json = new JsonObject();
            json.addProperty("nombre", tipo.name());
            json.addProperty("etiqueta", tipo.getEtiqueta());
            tipos.add(json);
        }

        JsonObject respuesta = new JsonObject();
        respuesta.add("usuarios", usuarios);
        respuesta.add("relaciones", relaciones);
        respuesta.add("tipos", tipos);
        respuesta.addProperty("siguienteId", grafo.getSiguienteId());
        respuesta.addProperty("pesoMaximo", Arco.PESO_MAXIMO);
        return respuesta;
    }

    private JsonElement ejemplo(String[] partes) {
        if (partes.length != 2 || !NOMBRE_EJEMPLO.matcher(partes[1]).matches()) {
            throw new ErrorHttp(404, "Ejemplo no encontrado");
        }
        try (InputStream entrada = ServidorWeb.class.getResourceAsStream("/ejemplos/" + partes[1] + ".json")) {
            if (entrada == null) {
                throw new ErrorHttp(404, "Ejemplo no encontrado: " + partes[1]);
            }
            return JsonParser.parseString(new String(entrada.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ErrorHttp(500, "No se pudo leer el ejemplo: " + e.getMessage());
        }
    }

    private void servirHtml(HttpExchange ex) throws IOException {
        try (InputStream entrada = ServidorWeb.class.getResourceAsStream("/web/index.html")) {
            if (entrada == null) {
                responderError(ex, 404, "Falta src/main/resources/web/index.html");
                return;
            }
            responder(ex, 200, "text/html; charset=utf-8", new String(entrada.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private void servirPagina(HttpExchange ex, String recurso) throws IOException {
        try (InputStream entrada = ServidorWeb.class.getResourceAsStream(recurso)) {
            if (entrada == null) {
                responderError(ex, 404, "Falta src/main/resources" + recurso);
                return;
            }
            responder(ex, 200, "text/html; charset=utf-8", new String(entrada.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    // Lectura de datos de la petición

    private String leerCuerpo(HttpExchange ex) throws IOException {
        try (InputStream entrada = ex.getRequestBody()) {
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Map<String, String> parsearConsulta(String consultaCruda) {
        Map<String, String> parametros = new HashMap<>();
        if (consultaCruda == null || consultaCruda.isEmpty()) return parametros;
        for (String par : consultaCruda.split("&")) {
            int igual = par.indexOf('=');
            String clave = igual < 0 ? par : par.substring(0, igual);
            String valor = igual < 0 ? "" : par.substring(igual + 1);
            parametros.put(
                URLDecoder.decode(clave, StandardCharsets.UTF_8),
                URLDecoder.decode(valor, StandardCharsets.UTF_8)
            );
        }
        return parametros;
    }

    private JsonObject cuerpoComoObjeto(String cuerpo) {
        if (cuerpo == null || cuerpo.trim().isEmpty()) {
            throw new IllegalArgumentException("El cuerpo de la petición está vacío");
        }
        JsonElement elemento;
        try {
            elemento = JsonParser.parseString(cuerpo);
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("JSON mal formado: " + e.getMessage());
        }
        if (!elemento.isJsonObject()) {
            throw new IllegalArgumentException("El cuerpo debe ser un objeto JSON");
        }
        return elemento.getAsJsonObject();
    }

    private int enteroDe(JsonObject objeto, String campo) {
        JsonElement elemento = objeto.get(campo);
        if (elemento == null || !elemento.isJsonPrimitive() || !elemento.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Falta el campo '" + campo + "' o no es un número");
        }
        double valor = elemento.getAsDouble();
        if (valor != Math.rint(valor) || Math.abs(valor) > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("El campo '" + campo + "' debe ser un número entero");
        }
        return (int) valor;
    }

    private double numeroDe(JsonObject objeto, String campo) {
        JsonElement elemento = objeto.get(campo);
        if (elemento == null || !elemento.isJsonPrimitive() || !elemento.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Falta el campo '" + campo + "' o no es un número");
        }
        return elemento.getAsDouble();
    }

    private PerfilEstudiante perfilDe(JsonObject objeto) {
        return cargador.leerPerfil(objeto);
    }

    private String textoDe(JsonObject objeto, String campo) {
        JsonElement elemento = objeto.get(campo);
        if (elemento == null || !elemento.isJsonPrimitive() || !elemento.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Falta el campo '" + campo + "' o no es texto");
        }
        return elemento.getAsString();
    }

    private TipoRelacion tipoDe(JsonObject objeto, String campo) {
        String texto = textoDe(objeto, campo).trim();
        try {
            return TipoRelacion.valueOf(texto.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Tipo de relación desconocido '" + texto + "'. Tipos válidos: "
                + Arrays.toString(TipoRelacion.values())
            );
        }
    }

    private int parametroEntero(Map<String, String> parametros, String nombre) {
        String valor = parametros.get(nombre);
        if (valor == null || valor.isEmpty()) {
            throw new IllegalArgumentException("Falta el parámetro '" + nombre + "'");
        }
        return aEntero(valor, nombre);
    }

    private int aEntero(String texto, String nombre) {
        try {
            return Integer.parseInt(texto.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + nombre + "' debe ser un número entero");
        }
    }

    private JsonArray listaEnteros(List<Integer> valores) {
        JsonArray lista = new JsonArray();
        for (int valor : valores) {
            lista.add(valor);
        }
        return lista;
    }

    // Respuestas

    private void exigir(String metodo, String esperado) {
        if (!esperado.equals(metodo)) {
            throw new ErrorHttp(405, "Método no permitido: se esperaba " + esperado);
        }
    }

    private void responder(HttpExchange ex, int codigo, String tipoContenido, String texto) throws IOException {
        byte[] datos = texto.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", tipoContenido);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(codigo, datos.length);
        try (OutputStream salida = ex.getResponseBody()) {
            salida.write(datos);
        }
    }

    private void responderError(HttpExchange ex, int codigo, String mensaje) throws IOException {
        JsonObject error = new JsonObject();
        error.addProperty("error", mensaje);
        responder(ex, codigo, "application/json; charset=utf-8", GSON.toJson(error));
    }

    private static class ErrorHttp extends RuntimeException {
        final int codigo;

        ErrorHttp(int codigo, String mensaje) {
            super(mensaje);
            this.codigo = codigo;
        }
    }
}