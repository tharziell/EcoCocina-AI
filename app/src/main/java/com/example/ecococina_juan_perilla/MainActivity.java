package com.example.ecococina_juan_perilla;

import android.content.ContentValues;
import android.content.Context;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private EditText etIngredientes;
    private ChipGroup chipGroupCondimentos;
    private Button btnGenerar, btnGuardarFav, btnLimpiar, btnBorrarHistorial;
    private TextView tvResultadoReceta, tvSinFavoritos;
    private LinearLayout llListaFavoritos;
    private ProgressBar progressBar;

    private RecetasDBHelper dbHelper;
    private String ultimaRecetaGenerada = "";
    private String ultimosIngredientes = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        etIngredientes = findViewById(R.id.etIngredientes);
        chipGroupCondimentos = findViewById(R.id.chipGroupCondimentos);
        btnGenerar = findViewById(R.id.btnGenerar);
        btnGuardarFav = findViewById(R.id.btnGuardarFav);
        btnLimpiar = findViewById(R.id.btnLimpiar);
        btnBorrarHistorial = findViewById(R.id.btnBorrarHistorial);
        tvResultadoReceta = findViewById(R.id.tvResultadoReceta);
        tvSinFavoritos = findViewById(R.id.tvSinFavoritos);
        llListaFavoritos = findViewById(R.id.llListaFavoritos);
        progressBar = findViewById(R.id.progressBar);

        configurarChipsCondimentos();

        dbHelper = new RecetasDBHelper(this);
        cargarFavoritosDesdeSQLite();

        btnGenerar.setOnClickListener(v -> {
            String ingredientes = etIngredientes.getText().toString().trim();
            if (ingredientes.isEmpty()) {
                Toast.makeText(this, "⚠️ Escribe al menos un ingrediente de tu nevera", Toast.LENGTH_SHORT).show();
                return;
            }
            String condimentos = obtenerCondimentosSeleccionados();
            ultimosIngredientes = ingredientes;
            generarRecetaConIA(ingredientes, condimentos);
        });

        btnGuardarFav.setOnClickListener(v -> {
            if (ultimaRecetaGenerada.isEmpty()) {
                Toast.makeText(this, "⚠️ Primero genera una receta con IA para guardarla", Toast.LENGTH_SHORT).show();
                return;
            }
            dbHelper.insertarReceta(ultimosIngredientes, ultimaRecetaGenerada);
            cargarFavoritosDesdeSQLite();
            Toast.makeText(this, "✅ Receta guardada en tu Recetario", Toast.LENGTH_SHORT).show();
        });

        btnLimpiar.setOnClickListener(v -> {
            etIngredientes.setText("");
            ultimaRecetaGenerada = "";
            tvResultadoReceta.setText("");
            Toast.makeText(this, "🔄 Campos listos para una nueva consulta", Toast.LENGTH_SHORT).show();
        });

        btnBorrarHistorial.setOnClickListener(v -> {
            dbHelper.vaciarTabla();
            cargarFavoritosDesdeSQLite();
            Toast.makeText(this, "🗑️ Historial de SQLite eliminado", Toast.LENGTH_SHORT).show();
        });
    }

    private int dpToPx(int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics()));
    }

    private void configurarChipsCondimentos() {
        for (int i = 0; i < chipGroupCondimentos.getChildCount(); i++) {
            View child = chipGroupCondimentos.getChildAt(i);
            if (child instanceof Chip) {
                Chip chip = (Chip) child;
                actualizarEstiloChip(chip, chip.isChecked());
                chip.setOnCheckedChangeListener((buttonView, isChecked) -> actualizarEstiloChip(chip, isChecked));
            }
        }
    }

    private void actualizarEstiloChip(Chip chip, boolean seleccionado) {
        if (seleccionado) {
            chip.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#D1FAE5")));
            chip.setChipStrokeColor(ColorStateList.valueOf(Color.parseColor("#059669")));
            chip.setTextColor(Color.parseColor("#065F46"));
            chip.setCheckedIconTint(ColorStateList.valueOf(Color.parseColor("#059669")));
        } else {
            chip.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#F8FAFC")));
            chip.setChipStrokeColor(ColorStateList.valueOf(Color.parseColor("#CBD5E1")));
            chip.setTextColor(Color.parseColor("#475569"));
        }
    }

    private String obtenerCondimentosSeleccionados() {
        List<String> seleccionados = new ArrayList<>();
        for (int i = 0; i < chipGroupCondimentos.getChildCount(); i++) {
            View child = chipGroupCondimentos.getChildAt(i);
            if (child instanceof Chip) {
                Chip chip = (Chip) child;
                if (chip.isChecked()) {
                    String texto = chip.getText().toString().trim();
                    int espacio = texto.indexOf(' ');
                    if (espacio != -1 && espacio + 1 < texto.length()) {
                        texto = texto.substring(espacio + 1).trim();
                    }
                    seleccionados.add(texto);
                }
            }
        }
        return String.join(", ", seleccionados);
    }

    private String obtenerMomentoDelDia() {
        int hora = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hora >= 5 && hora < 12) {
            return "desayuno";
        } else if (hora >= 12 && hora < 16) {
            return "almuerzo";
        } else if (hora >= 16 && hora < 19) {
            return "merienda";
        } else {
            return "cena";
        }
    }

    private String extraerTituloReceta(String rec, int id) {
        if (rec == null || rec.trim().isEmpty()) {
            return "Receta #" + id;
        }
        String[] lineas = rec.split("\\r?\\n");
        for (int i = 0; i < lineas.length; i++) {
            String l = lineas[i].trim();
            if (l.isEmpty()) continue;

            String sinEmoji = l.replace("🍽️", "").trim();
            String upper = sinEmoji.toUpperCase(Locale.getDefault());

            if (upper.startsWith("NOMBRE DEL PLATO")) {
                int dosPuntos = sinEmoji.indexOf(':');
                if (dosPuntos != -1 && dosPuntos + 1 < sinEmoji.length()) {
                    String despues = sinEmoji.substring(dosPuntos + 1).trim();
                    if (!despues.isEmpty()) return despues;
                }
                for (int j = i + 1; j < lineas.length; j++) {
                    String siguiente = lineas[j].trim().replace("🍽️", "").trim();
                    if (!siguiente.isEmpty() && !siguiente.startsWith("⏱️")) {
                        return siguiente;
                    }
                }
            } else {
                return sinEmoji;
            }
        }
        return "Receta #" + id;
    }

    private CharSequence formatearTextoBonito(String texto) {
        return formatearTextoBonito(texto, false);
    }

    private CharSequence formatearTextoBonito(String texto, boolean modoOscuro) {
        SpannableStringBuilder ssb = new SpannableStringBuilder();
        String[] lineas = texto.split("\\r?\\n");

        int colorTitulo = modoOscuro ? Color.parseColor("#34D399") : Color.parseColor("#9A3412");
        int colorEncabezadoReceta = modoOscuro ? Color.parseColor("#FBBF24") : Color.parseColor("#065F46");
        int colorVineta = modoOscuro ? Color.parseColor("#34D399") : Color.parseColor("#059669");
        int colorTiempoPaso = modoOscuro ? Color.parseColor("#FDBA74") : Color.parseColor("#EA580C");
        int colorSeparador = modoOscuro ? Color.parseColor("#475569") : Color.parseColor("#CBD5E1");

        Pattern patronPaso = Pattern.compile("^(\\d+[.)])");
        Pattern patronTiempo = Pattern.compile("(?i)\\b\\d+(?:\\s*(?:[-–—]|a)\\s*\\d+)?\\s*(?:minutos?|min\\.?|segundos?|seg\\.?|horas?)\\b");

        for (String linea : lineas) {
            String trim = linea.trim();
            if (trim.isEmpty()) {
                continue;
            }

            boolean esTituloSeccion = trim.startsWith("🍽️") || trim.startsWith("⏱️")
                    || trim.startsWith("🥕") || trim.startsWith("🧂")
                    || trim.startsWith("👨‍🍳") || trim.startsWith("💡");
            Matcher matcherPaso = patronPaso.matcher(trim);
            boolean esPaso = matcherPaso.find();

            if (ssb.length() > 0) {
                ssb.append("\n");
                if (esPaso || esTituloSeccion) {
                    ssb.append("\n");
                }
            }

            int inicio = ssb.length();

            if (trim.startsWith("📌")) {
                ssb.append(trim);
                ssb.setSpan(new StyleSpan(Typeface.BOLD), inicio, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new ForegroundColorSpan(colorEncabezadoReceta), inicio, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new RelativeSizeSpan(1.06f), inicio, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (esTituloSeccion) {
                ssb.append(trim);
                ssb.setSpan(new StyleSpan(Typeface.BOLD), inicio, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new ForegroundColorSpan(colorTitulo), inicio, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new RelativeSizeSpan(1.05f), inicio, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (trim.startsWith("• ")) {
                String contenido = trim.substring(2).trim();
                ssb.append("   ▸ ");
                ssb.setSpan(new ForegroundColorSpan(colorVineta), inicio, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new StyleSpan(Typeface.BOLD), inicio, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                int inicioContenido = ssb.length();
                ssb.append(contenido);
                int idxDosPuntos = contenido.indexOf(':');
                int idxGuion = contenido.indexOf(" - ");
                int corte = idxDosPuntos > 0 ? idxDosPuntos : idxGuion;
                if (corte > 0) {
                    ssb.setSpan(new StyleSpan(Typeface.BOLD), inicioContenido, inicioContenido + corte, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            } else if (trim.startsWith("━━")) {
                ssb.append(trim);
                ssb.setSpan(new ForegroundColorSpan(colorSeparador), inicio, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (esPaso) {
                String numeroPaso = matcherPaso.group(1);
                String resto = trim.substring(numeroPaso.length());
                ssb.append(numeroPaso);
                ssb.setSpan(new StyleSpan(Typeface.BOLD), inicio, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new ForegroundColorSpan(colorTiempoPaso), inicio, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                int inicioResto = ssb.length();
                ssb.append(resto);
                Matcher matcherTiempo = patronTiempo.matcher(resto);
                while (matcherTiempo.find()) {
                    ssb.setSpan(new StyleSpan(Typeface.BOLD), inicioResto + matcherTiempo.start(), inicioResto + matcherTiempo.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    ssb.setSpan(new ForegroundColorSpan(colorTiempoPaso), inicioResto + matcherTiempo.start(), inicioResto + matcherTiempo.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            } else {
                ssb.append(trim);
            }
        }
        return ssb;
    }

    private void generarRecetaConIA(String ingredientes, String condimentos) {
        progressBar.setVisibility(View.VISIBLE);
        btnGenerar.setEnabled(false);
        tvResultadoReceta.setText("");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            String respuestaIA;
            boolean exito = false;

            try {
                String momento = obtenerMomentoDelDia();
                String horaActual = new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date());

                String reglaCondimentos = condimentos.isEmpty()
                        ? "Sin condimentos seleccionados; usa solo agua si hace falta y NO inventes salsas ni condimentos."
                        : "Condimentos disponibles: " + condimentos
                          + " (y agua). Usa SOLO estos y NO agregues salsas ni ingredientes fuera de la lista.";

                String prompt = "Eres un chef asistente de cocina en español. "
                        + "Son las " + horaActual + ", hora de " + momento + ". "
                        + "Crea UNA sola receta práctica y deliciosa para " + momento
                        + " usando ÚNICAMENTE estos ingredientes principales: " + ingredientes + ". "
                        + reglaCondimentos + " "
                        + "Responde en texto limpio para celular: sin tablas, sin el símbolo |, sin asteriscos y sin preguntas al final. "
                        + "Usa exactamente este orden y formato con saltos de línea claros:\n"
                        + "🍽️ NOMBRE DEL PLATO (ideal para " + momento + ")\n"
                        + "⏱️ TIEMPO TOTAL Y PORCIONES\n"
                        + "🥕 INGREDIENTES PRINCIPALES\n"
                        + "(lista ordenada, un ingrediente por línea con el formato exacto: • Nombre del ingrediente: cantidad)\n"
                        + "🧂 CONDIMENTOS Y BÁSICOS\n"
                        + "(lista ordenada, uno por línea con el formato exacto: • Nombre del condimento: cantidad)\n"
                        + "👨‍🍳 PREPARACIÓN PASO A PASO\n"
                        + "(pasos numerados 1., 2., 3. detallados; NO pongas el tiempo entre paréntesis al inicio del paso, "
                        + "indica de forma natural dentro del texto de cada paso cuánto tiempo toma esa acción, por ejemplo 'sofríe durante 4 minutos' o 'cocina a fuego bajo por 12 minutos', "
                        + "asegurando que coincidan con el tiempo total)\n"
                        + "💡 CONSEJO DEL CHEF\n"
                        + "(un tip breve)";

                String promptCodificado = URLEncoder.encode(prompt, "UTF-8").replace("+", "%20");
                int seed = (int) (Math.random() * 90000) + 10000;
                String urlApi = "https://text.pollinations.ai/" + promptCodificado + "?model=openai&seed=" + seed;

                URL url = new URL(urlApi);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14)");
                conn.setRequestProperty("Accept", "text/plain, */*");
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(30000);

                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                    br.close();

                    respuestaIA = sb.toString()
                            .replace("**", "")
                            .replace("### ", "")
                            .replace("## ", "")
                            .replace("# ", "")
                            .replaceAll("(?m)^\\s*-\\s+", "• ")
                            .replaceAll("(?m)^\\s*\\*\\s+", "• ")
                            .replaceAll("\\n{3,}", "\n\n")
                            .trim();
                    exito = !respuestaIA.isEmpty();
                } else {
                    respuestaIA = "Error del servidor (" + conn.getResponseCode() + "). Intenta de nuevo.";
                }
            } catch (Exception e) {
                respuestaIA = "Error de conexión: " + e.getMessage();
            }

            final String resultadoFinal = respuestaIA;
            final boolean recetaValida = exito;

            handler.post(() -> {
                progressBar.setVisibility(View.GONE);
                btnGenerar.setEnabled(true);
                ultimaRecetaGenerada = recetaValida ? resultadoFinal : "";
                tvResultadoReceta.setText(recetaValida ? formatearTextoBonito(resultadoFinal) : resultadoFinal);
            });
        });
    }

    private void cargarFavoritosDesdeSQLite() {
        llListaFavoritos.removeAllViews();
        List<RecetaItem> recetas = dbHelper.obtenerListaRecetas();

        if (recetas.isEmpty()) {
            tvSinFavoritos.setVisibility(View.VISIBLE);
            return;
        }

        tvSinFavoritos.setVisibility(View.GONE);

        for (RecetaItem item : recetas) {
            MaterialCardView card = new MaterialCardView(this);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            cardParams.bottomMargin = dpToPx(10);
            card.setLayoutParams(cardParams);
            card.setCardBackgroundColor(Color.parseColor("#1E293B"));
            card.setStrokeColor(Color.parseColor("#334155"));
            card.setStrokeWidth(dpToPx(1));
            card.setRadius(dpToPx(14));
            card.setCardElevation(dpToPx(2));
            card.setClickable(true);
            card.setFocusable(true);

            LinearLayout contenedor = new LinearLayout(this);
            contenedor.setOrientation(LinearLayout.VERTICAL);
            int pad = dpToPx(14);
            contenedor.setPadding(pad, pad, pad, pad);

            LinearLayout filaSuperior = new LinearLayout(this);
            filaSuperior.setOrientation(LinearLayout.HORIZONTAL);

            TextView tvMeta = new TextView(this);
            LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tvMeta.setLayoutParams(metaParams);
            tvMeta.setText("📌 #" + item.id + "  •  " + item.fecha);
            tvMeta.setTextColor(Color.parseColor("#34D399"));
            tvMeta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            tvMeta.setTypeface(null, Typeface.BOLD);

            TextView tvAbrir = new TextView(this);
            tvAbrir.setText("Abrir ▸");
            tvAbrir.setTextColor(Color.parseColor("#FDBA74"));
            tvAbrir.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tvAbrir.setTypeface(null, Typeface.BOLD);

            filaSuperior.addView(tvMeta);
            filaSuperior.addView(tvAbrir);

            TextView tvTitulo = new TextView(this);
            tvTitulo.setText(extraerTituloReceta(item.receta, item.id));
            tvTitulo.setTextColor(Color.parseColor("#F8FAFC"));
            tvTitulo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            tvTitulo.setTypeface(null, Typeface.BOLD);
            tvTitulo.setMaxLines(2);
            tvTitulo.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams tituloParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            tituloParams.topMargin = dpToPx(5);
            tvTitulo.setLayoutParams(tituloParams);

            TextView tvIng = new TextView(this);
            tvIng.setText("🥕 " + item.ingredientes);
            tvIng.setTextColor(Color.parseColor("#94A3B8"));
            tvIng.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tvIng.setMaxLines(1);
            tvIng.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams ingParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            ingParams.topMargin = dpToPx(4);
            tvIng.setLayoutParams(ingParams);

            contenedor.addView(filaSuperior);
            contenedor.addView(tvTitulo);
            contenedor.addView(tvIng);
            card.addView(contenedor);

            card.setOnClickListener(v -> mostrarRecetaCompletaDialog(item));

            llListaFavoritos.addView(card);
        }
    }

    private void mostrarRecetaCompletaDialog(RecetaItem item) {
        String tituloPlato = extraerTituloReceta(item.receta, item.id);

        StringBuilder despensa = new StringBuilder();
        for (String ing : item.ingredientes.split(",")) {
            String limpio = ing.trim();
            if (!limpio.isEmpty()) {
                limpio = limpio.substring(0, 1).toUpperCase(Locale.getDefault()) + limpio.substring(1);
                despensa.append("• ").append(limpio).append("\n");
            }
        }

        String contenidoCompleto = "📌 Guardada el " + item.fecha + "\n\n"
                + "🥕 DESPENSA USADA\n"
                + despensa + "\n"
                + item.receta;

        ScrollView scroll = new ScrollView(this);
        TextView tvDetalle = new TextView(this);
        int padH = dpToPx(22);
        int padV = dpToPx(14);
        tvDetalle.setPadding(padH, padV, padH, padV);
        tvDetalle.setTextColor(Color.parseColor("#334155"));
        tvDetalle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tvDetalle.setLineSpacing(dpToPx(4), 1.0f);
        tvDetalle.setText(formatearTextoBonito(contenidoCompleto));
        scroll.addView(tvDetalle);

        new MaterialAlertDialogBuilder(this)
                .setTitle("🍽️ " + tituloPlato)
                .setView(scroll)
                .setPositiveButton("Cerrar", null)
                .setNegativeButton("🗑️ Eliminar", (dialog, which) -> {
                    dbHelper.eliminarRecetaPorId(item.id);
                    cargarFavoritosDesdeSQLite();
                    Toast.makeText(this, "🗑️ Receta eliminada del recetario", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private static class RecetaItem {
        final int id;
        final String fecha;
        final String ingredientes;
        final String receta;

        RecetaItem(int id, String fecha, String ingredientes, String receta) {
            this.id = id;
            this.fecha = fecha;
            this.ingredientes = ingredientes;
            this.receta = receta;
        }
    }

    private static class RecetasDBHelper extends SQLiteOpenHelper {
        private static final String DB_NAME = "EcoCocinaDB.db";
        private static final int DB_VERSION = 1;
        private static final String TABLE_NAME = "recetas_fav";

        public RecetasDBHelper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            String createTable = "CREATE TABLE " + TABLE_NAME + " ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "fecha TEXT, "
                    + "ingredientes TEXT, "
                    + "receta TEXT)";
            db.execSQL(createTable);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
            onCreate(db);
        }

        public void insertarReceta(String ingredientes, String receta) {
            SQLiteDatabase db = this.getWritableDatabase();
            ContentValues values = new ContentValues();
            String fechaActual = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date());
            values.put("fecha", fechaActual);
            values.put("ingredientes", ingredientes);
            values.put("receta", receta);
            db.insert(TABLE_NAME, null, values);
            db.close();
        }

        public List<RecetaItem> obtenerListaRecetas() {
            List<RecetaItem> lista = new ArrayList<>();
            SQLiteDatabase db = this.getReadableDatabase();
            Cursor cursor = db.rawQuery("SELECT id, fecha, ingredientes, receta FROM " + TABLE_NAME + " ORDER BY id DESC", null);

            while (cursor.moveToNext()) {
                lista.add(new RecetaItem(
                        cursor.getInt(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getString(3)
                ));
            }
            cursor.close();
            db.close();
            return lista;
        }

        public void eliminarRecetaPorId(int id) {
            SQLiteDatabase db = this.getWritableDatabase();
            db.delete(TABLE_NAME, "id = ?", new String[]{String.valueOf(id)});
            db.close();
        }

        public void vaciarTabla() {
            SQLiteDatabase db = this.getWritableDatabase();
            db.execSQL("DELETE FROM " + TABLE_NAME);
            db.close();
        }
    }
}