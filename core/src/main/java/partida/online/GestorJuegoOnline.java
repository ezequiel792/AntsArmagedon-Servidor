package partida.online;

import Fisicas.Borde;
import Fisicas.Camara;
import Fisicas.Fisica;
import Fisicas.Mapa;
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
import entradas.ControlesJugador;
import hud.Hud;
import screens.GameOverScreen;
import utils.RecursosGlobales;

import java.util.ArrayList;
import java.util.List;

public final class GestorJuegoOnline {

    private final List<Jugador> jugadores = new ArrayList<>();
    private final GestorColisiones gestorColisiones;
    private final GestorProyectiles gestorProyectiles;
    private final GestorEntidades gestorEntidades;
    private final GestorFisica gestorFisica;
    private final GestorSpawn gestorSpawn;
    private final GestorTurno gestorTurno;

    private int turnosCompletados;
    private final int frecuenciaPowerUps;

    public GestorJuegoOnline(
        List<Jugador> jugadores,
        GestorColisiones gestorColisiones,
        GestorProyectiles gestorProyectiles,
        GestorSpawn gestorSpawn,
        Fisica fisica,
        int tiempoPorTurno,
        int frecuenciaPowerUps,
        Mapa mapa
    ) {
        this.jugadores.addAll(jugadores);
        this.gestorColisiones = gestorColisiones;
        this.gestorProyectiles = gestorProyectiles;
        this.gestorSpawn = gestorSpawn;
        this.frecuenciaPowerUps = frecuenciaPowerUps;

        this.gestorTurno = new GestorTurno(new ArrayList<>(jugadores), tiempoPorTurno);
        this.gestorFisica = new GestorFisica(fisica, gestorColisiones);
        this.gestorEntidades = new GestorEntidades(gestorFisica, gestorColisiones);

        gestorSpawn.precalcularPuntosValidos(16f, 16f);

        new Borde(gestorColisiones);

        for (Jugador jugador : this.jugadores) {
            for (Personaje personaje : jugador.getPersonajes()) {
                this.gestorEntidades.agregarEntidad(personaje);
            }
        }
    }

    public void actualizar(float delta, Mapa mapa) {
        int turnoAntes = gestorTurno.getTurnoActual();

        gestorTurno.correrContador(delta);
        revisarPersonajesMuertos();

        gestorEntidades.actualizar(delta);
        gestorProyectiles.actualizar(delta);

        int turnoActual = gestorTurno.getTurnoActual();

        if (turnoActual != turnoAntes) {
            turnosCompletados++;

            Jugador jugadorAnterior = jugadores.get(turnoAntes);
            jugadorAnterior.getPersonajeActivo().setEnTurno(false);

            Jugador jugadorActual = jugadores.get(turnoActual);
            jugadorActual.getPersonajeActivo().setEnTurno(true);

            if (turnosCompletados > 0 && turnosCompletados % frecuenciaPowerUps == 0) {
                generarPowerUp();
            }
        }
    }

    public void procesarEntradaJugador(ControlesJugador control, float delta) {
        if (control == null || gestorTurno.isEnTransicion()) return;

        Personaje activo = getPersonajeActivo();
        if (activo == null || activo.isTurnoTerminado()) return;

        control.procesarEntrada();

        if (activo.isDisparando()) {
            if (control.getApuntarDir() != 0)
                activo.apuntar(control.getApuntarDir());

            if (control.getDisparoLiberado()) {
                activo.usarMovimiento();
                control.resetDisparoLiberado();
            }

            activo.actualizarDisparo(delta);
            return;
        }

        float x = control.getX();
        activo.mover(x, delta);

        if (control.getSaltar())
            activo.saltar();

        if (control.getApuntarDir() != 0)
            activo.apuntar(control.getApuntarDir());

        activo.setMovimientoSeleccionado(control.getMovimientoSeleccionado());

        if (control.getDisparoPresionado())
            activo.usarMovimiento();
    }

    private void revisarPersonajesMuertos() {
        List<Jugador> jugadoresSinPersonajes = new ArrayList<>();

        for (Jugador jugador : jugadores) {
            List<Personaje> muertos = new ArrayList<>();
            for (Personaje personaje : jugador.getPersonajes()) {
                if (!personaje.getActivo()) muertos.add(personaje);
            }
            for (Personaje personaje : muertos) jugador.removerPersonaje(personaje);
            if (!jugador.estaVivo()) jugadoresSinPersonajes.add(jugador);
        }

        jugadores.removeAll(jugadoresSinPersonajes);

        if (jugadores.size() <= 1) indicarGanador();
    }

    private void indicarGanador() {
        String mensajeFinal;

        if (jugadores.isEmpty()) {
            mensajeFinal = "Empate";
        } else {
            Jugador ganador = jugadores.get(0);
            mensajeFinal = "Jugador " + (ganador.getIdJugador() + 1) + " gana!";
        }

        GestorScreen.setScreen(new GameOverScreen(GestorScreen.returnJuego(), mensajeFinal));
    }

    private void generarPowerUp() {
        Vector2 spawnPower = gestorSpawn.generarSpawnPowerUp(8f);
        if (spawnPower != null) {
            PowerUp nuevoPower = new CajaVida(spawnPower.x, spawnPower.y, gestorColisiones);
            agregarEntidad(nuevoPower);
            System.out.println("PowerUp generado en: " + spawnPower);
        }
    }

    public void renderDebug(ShapeRenderer shapeRenderer, Camara camara) {
        gestorEntidades.renderDebug(shapeRenderer, camara);

        for (Jugador j : jugadores) {
            for (Personaje p : j.getPersonajes()) {
                Movimiento m = p.getMovimientoSeleccionado();
                if (m instanceof MovimientoMelee mm) {
                    mm.renderGolpe(shapeRenderer, Gdx.graphics.getDeltaTime());
                }
            }
        }
    }

    public void renderEntidades(SpriteBatch batch) { gestorEntidades.render(batch); }

    public void renderProyectiles(SpriteBatch batch) { gestorProyectiles.render(batch); }

    public void renderPersonajes(Hud hud) {
        for (Jugador jugador : jugadores) {
            for (Personaje personaje : jugador.getPersonajes()) {
                personaje.render(RecursosGlobales.batch);
                hud.mostrarVida(personaje);
            }
        }
    }

    public void registrarDisparoRemoto(int numJugador, float angulo, float potencia) {
        try {
            if (numJugador < 0 || numJugador >= jugadores.size()) return;

            Jugador jugador = jugadores.get(numJugador);
            Personaje pj = jugador.getPersonajeActivo();
            if (pj == null || !pj.getActivo()) return;

            pj.setEnTurno(true);
            pj.getMirilla().setAngulo(angulo);

            var mov = pj.getMovimientoSeleccionado();
            if (mov == null) return;

            try {
                mov.getClass().getMethod("ejecutar", Personaje.class, float.class)
                    .invoke(mov, pj, potencia);
            } catch (NoSuchMethodException e) {
                mov.getClass().getMethod("ejecutar", Personaje.class).invoke(mov, pj);
            }

            pj.terminarTurno();
            System.out.println("[GESTOR ONLINE] Disparo remoto reproducido (jugador=" + numJugador + ")");

        } catch (Exception ex) {
            System.err.println("[GESTOR ONLINE] registrarDisparoRemoto error: " + ex.getMessage());
        }
    }

    public void procesarImpactoRemoto(float x, float y, int danio, boolean destruyeTerreno) {
        try {
            final float radioDanio   = Math.max(40f, danio * 1.25f);
            final int   radioTerreno = (int) Math.max(18, danio * 0.6f);

            var afectados = gestorColisiones.getColisionablesRadio(x, y, radioDanio);
            for (var col : afectados) {
                if (col instanceof Personaje p && p.getActivo()) {
                    float dist = p.distanciaAlCentro(x, y);
                    if (dist <= radioDanio) {
                        float factor = 1f - (dist / radioDanio);
                        int danoAplicado = Math.max(0, Math.round(danio * factor));
                        p.recibirDanio(danoAplicado, 0, 0);
                    }
                }
            }

            if (destruyeTerreno) {
                Mapa m = gestorColisiones.getMapa();
                if (m != null) m.destruir(x, y, radioTerreno);
            }

            System.out.println("[GESTOR ONLINE] Impacto remoto procesado en (" + x + ", " + y + ")");

        } catch (Exception ex) {
            System.err.println("[GESTOR ONLINE] procesarImpactoRemoto error: " + ex.getMessage());
        }
    }

    public Personaje getPersonajeActivo() {
        Jugador jugadorActivo = getJugadorActivo();
        return (jugadorActivo != null) ? jugadorActivo.getPersonajeActivo() : null;
    }

    public void agregarEntidad(Entidad entidad) {
        gestorEntidades.agregarEntidad(entidad);
    }

    public void dispose() {
        gestorProyectiles.dispose();
        gestorEntidades.dispose();
    }

    public Jugador getJugadorActivo() { return gestorTurno.getJugadorActivo(); }
    public int getTurnoActual() { return gestorTurno.getTurnoActual(); }
    public float getTiempoActual() { return gestorTurno.getTiempoActual(); }
    public List<Jugador> getJugadores() { return jugadores; }
    public GestorColisiones getGestorColisiones() { return gestorColisiones; }
    public GestorProyectiles getGestorProyectiles() { return gestorProyectiles; }
    public GestorFisica getGestorFisica() { return gestorFisica; }
    public GestorTurno getGestorTurno() { return gestorTurno; }
}
