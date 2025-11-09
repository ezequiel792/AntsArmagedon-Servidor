package partida.online;

import Fisicas.Camara;
import Fisicas.Fisica;
import Gameplay.Gestores.GestorTurno;
import Gameplay.Gestores.Logicos.*;
import Gameplay.Movimientos.Movimiento;
import Gameplay.Movimientos.MovimientoMelee;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.principal.Jugador;
import entidades.Entidad;
import entidades.PowerUps.CajaVida;
import entidades.PowerUps.PowerUp;
import entidades.personajes.Personaje;
import entidades.proyectiles.Proyectil;
import hud.Hud;
import network.ServerThread;
import network.paquetes.entidades.PaqueteImpacto;
import network.paquetes.entidades.PaquetePowerUp;
import network.paquetes.partida.PaqueteCambioTurno;
import network.paquetes.partida.PaqueteFinPartida;
import network.paquetes.personaje.PaqueteDisparar;
import network.paquetes.utilidad.DatosJuego;
import utils.RecursosGlobales;

import java.util.ArrayList;
import java.util.List;

public final class GestorJuegoServidor {

    private final List<Jugador> jugadores = new ArrayList<>();
    private final GestorColisiones gestorColisiones;
    private final GestorProyectiles gestorProyectiles;
    private final GestorEntidades gestorEntidades;
    private final GestorFisica gestorFisica;
    private final GestorSpawn gestorSpawn;
    private final GestorTurno gestorTurno;
    private final ServerThread serverThread;

    private final int frecuenciaPowerUps;
    private int turnosCompletados = 0;

    public GestorJuegoServidor(
        List<Jugador> jugadores,
        GestorColisiones gestorColisiones,
        GestorProyectiles gestorProyectiles,
        GestorSpawn gestorSpawn,
        Fisica fisica,
        int tiempoPorTurno,
        int frecuenciaPowerUps,
        ServerThread serverThread
    ) {
        this.jugadores.addAll(jugadores);
        this.gestorColisiones = gestorColisiones;
        this.gestorProyectiles = gestorProyectiles;
        this.gestorSpawn = gestorSpawn;
        this.frecuenciaPowerUps = frecuenciaPowerUps;
        this.serverThread = serverThread;

        this.gestorTurno = new GestorTurno(new ArrayList<>(jugadores), tiempoPorTurno);
        this.gestorFisica = new GestorFisica(fisica, gestorColisiones);
        this.gestorEntidades = new GestorEntidades(gestorFisica, gestorColisiones);

        for (Jugador jugador : this.jugadores) {
            for (Personaje personaje : jugador.getPersonajes()) {
                this.gestorEntidades.agregarEntidad(personaje);
            }
        }
    }

    public void actualizar(float delta) {
        int turnoAntes = gestorTurno.getTurnoActual();

        gestorTurno.correrContador(delta);

        revisarPersonajesMuertos();

        gestorEntidades.actualizar(delta);
        gestorProyectiles.actualizar(delta);

        int turnoActual = gestorTurno.getTurnoActual();
        if (turnoActual != turnoAntes) {
            onCambioTurno(turnoAntes, turnoActual);
        }
    }

    private void onCambioTurno(int turnoAnterior, int turnoActual) {
        turnosCompletados++;

        Jugador anterior = jugadores.get(turnoAnterior);
        if (anterior.getPersonajeActivo() != null)
            anterior.getPersonajeActivo().setEnTurno(false);

        Jugador actual = jugadores.get(turnoActual);
        Personaje p = actual.getPersonajeActivo();
        if (p != null) p.setEnTurno(true);

        if (turnosCompletados > 0 && turnosCompletados % frecuenciaPowerUps == 0) {
            generarPowerUp();
        }

        int turno = turnoActual;
        float tiempoRestante = gestorTurno.getTiempoActual();
        int jugadorId = actual.getIdJugador();
        int personajeIndex = actual.getIndicePersonajeActivo();

        serverThread.enviarATodos(new PaqueteCambioTurno(turno, tiempoRestante, jugadorId, personajeIndex));

        System.out.println("[SERVIDOR] Cambio de turno -> Jugador " + jugadorId +
            " | Personaje " + personajeIndex +
            " | Tiempo restante: " + tiempoRestante);

        System.out.println("[SERVIDOR] Estado actual de jugadores:");
        for (Jugador j : jugadores) {
            j.imprimirEstadoDebug();
        }

    }


    public void moverPersonaje(int numJugador, float direccion) {
        Personaje p = getPersonaje(numJugador);
        if (p != null && p.getActivo()) {
            p.mover(direccion, Gdx.graphics.getDeltaTime());
        }
    }

    public void saltarPersonaje(int numJugador) {
        Personaje p = getPersonaje(numJugador);
        if (p != null && p.getActivo()) {
            p.saltar();
        }
    }

    public void apuntarPersonaje(int numJugador, int direccion) {
        Personaje p = getPersonaje(numJugador);
        if (p != null && p.getActivo()) {
            p.apuntar(direccion);
        }
    }

    public void dispararPersonaje(int numJugador, float angulo, float potencia) {
        Personaje p = getPersonaje(numJugador);
        if (p == null || !p.getActivo()) return;

        p.getMirilla().setAngulo(angulo);
        Movimiento mov = p.getMovimientoSeleccionado();
        if (mov != null) mov.ejecutar(p);
        p.terminarTurno();

        serverThread.enviarATodos(new PaqueteDisparar(numJugador, angulo, potencia));
    }


    public void cambiarMovimientoPersonaje(int numJugador, int indice) {
        Personaje p = getPersonaje(numJugador);
        if (p != null) p.setMovimientoSeleccionado(indice);
    }

    public void procesarImpactoRemoto(float x, float y, int danio, boolean destruye) {
        serverThread.enviarATodos(new PaqueteImpacto(x, y, danio, destruye));
    }


    public void cambiarTurno() {
        gestorTurno.forzarFinTurno();
    }

    public int getTurnoActual() { return gestorTurno.getTurnoActual(); }

    private void generarPowerUp() {
        PowerUp dummy = new CajaVida(0, 0, gestorColisiones);
        Vector2 pos = gestorSpawn.generarSpawnPowerUp(dummy);
        if (pos != null) {
            agregarEntidad(new CajaVida(pos.x, pos.y, gestorColisiones));
            serverThread.enviarATodos(new PaquetePowerUp(pos.x, pos.y));
        }
    }

    private void revisarPersonajesMuertos() {
        List<Jugador> muertos = new ArrayList<>();

        for (Jugador j : jugadores) {
            List<Personaje> aEliminar = j.getPersonajes().stream()
                .filter(p -> !p.getActivo())
                .toList();

            aEliminar.forEach(j::removerPersonaje);
            if (!j.estaVivo()) muertos.add(j);
        }

        jugadores.removeAll(muertos);
        if (jugadores.size() <= 1) indicarGanador();
    }

    private void indicarGanador() {
        int ganadorId = jugadores.isEmpty() ? -1 : jugadores.get(0).getIdJugador();
        serverThread.enviarATodos(new PaqueteFinPartida(ganadorId));
        System.out.println("[SERVIDOR] " + (ganadorId == -1 ? "Empate" : "Jugador " + (ganadorId + 1) + " gana!"));
    }


    public void renderEntidades(SpriteBatch batch) { gestorEntidades.render(batch); }
    public void renderProyectiles(SpriteBatch batch) { gestorProyectiles.render(batch); }

    public void renderPersonajes(Hud hud) {
        for (Jugador j : jugadores)
            for (Personaje p : j.getPersonajes()) {
                p.render(RecursosGlobales.batch);
                hud.mostrarVida(p);
            }
    }

    public void renderDebug(ShapeRenderer shapeRenderer, Camara camara) {
        gestorEntidades.renderDebug(shapeRenderer, camara);
        for (Jugador j : jugadores)
            for (Personaje p : j.getPersonajes())
                if (p.getMovimientoSeleccionado() instanceof MovimientoMelee mm)
                    mm.renderGolpe(shapeRenderer, Gdx.graphics.getDeltaTime());
    }

    private Personaje getPersonaje(int numJugador) {
        if (numJugador < 0 || numJugador >= jugadores.size()) return null;
        return jugadores.get(numJugador).getPersonajeActivo();
    }

    public Personaje getPersonajeActivo() {
        Jugador j = gestorTurno.getJugadorActivo();
        return j != null ? j.getPersonajeActivo() : null;
    }

    public List<Jugador> getJugadores() { return jugadores; }
    public GestorTurno getGestorTurno() { return gestorTurno; }
    public GestorProyectiles getGestorProyectiles() { return gestorProyectiles; }
    public float getTiempoActual() { return gestorTurno.getTiempoActual(); }
    public GestorSpawn getGestorSpawn() { return gestorSpawn; }

    public void agregarEntidad(Entidad entidad) { gestorEntidades.agregarEntidad(entidad); }

    public void dispose() {
        gestorProyectiles.dispose();
        gestorEntidades.dispose();
    }

    public List<DatosJuego.EntidadDTO> obtenerEntidadesDTO() {
        List<DatosJuego.EntidadDTO> lista = new ArrayList<>();
        for (Entidad e : gestorEntidades.getEntidades()) {
            DatosJuego.EntidadDTO dto = new DatosJuego.EntidadDTO();
            dto.id = e.getIdEntidad();
            dto.x = e.getX();
            dto.y = e.getY();
            dto.activa = e.getActivo();
            lista.add(dto);
        }
        return lista;
    }

    public List<DatosJuego.ProyectilDTO> obtenerProyectilesDTO() {
        List<DatosJuego.ProyectilDTO> lista = new ArrayList<>();
        for (Proyectil p : gestorProyectiles.getProyectiles()) {
            DatosJuego.ProyectilDTO dto = new DatosJuego.ProyectilDTO();
            dto.id = p.getIdProyectil();
            dto.x = p.getX();
            dto.y = p.getY();
            dto.activo = p.getActivo();
            lista.add(dto);
        }
        return lista;
    }
}
