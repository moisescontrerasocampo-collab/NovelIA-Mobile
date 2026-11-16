package cl.cantalocal.autonomo;

import android.app.Activity;
import android.app.DownloadManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Bundle;
import android.os.StatFs;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final int PORT = 8085;
    private static final String HOST = "http://127.0.0.1:8085";
    private static final String REV = "83f32e465c3abd605271d0c1c935ad2faf28c197";
    private static final String BASE = "https://huggingface.co/Serveurperso/ACE-Step-1.5-GGUF/resolve/" + REV + "/";
    private static final String[][] MODELS = {
        {"acestep-5Hz-lm-0.6B-Q8_0.gguf", "bdaf9e292d4470f31c19cafeaca1b74936a114667e3a85e5d33b65247e9908ec", "650000000"},
        {"acestep-v15-turbo-Q4_K_M.gguf", "55b4d8514850f3d0f82536f37e99673aaf48df802b5ae5b153eea32a2e2daa5e", "1300000000"},
        {"Qwen3-Embedding-0.6B-Q8_0.gguf", "972f23255e46adfe744a0eb9a0039f3c63988f65753b0968d776e8b27168c321", "700000000"},
        {"vae-BF16.gguf", "0599862ac5d15cd308e1d2e368373aea6c02e25ebd1737ad4a4562a0901b0ef8", "310000000"}
    };

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Object lock = new Object();
    private boolean working = false;
    private Process aceProcess;
    private File modelDir;
    private SharedPreferences prefs;
    private TextView state, description;
    private ProgressBar progress;
    private Button setupButton;
    private WebView web;
    private LinearLayout setupPage;
    private DownloadManager downloadManager;
    private final Handler statusHandler = new Handler(Looper.getMainLooper());
    private final Runnable statusTick = new Runnable() { @Override public void run() { refreshDownloads(); statusHandler.postDelayed(this, 2000); } };
    private volatile boolean verifying = false;
    private volatile boolean launching = false;

    @Override public void onCreate(Bundle stateBundle) {
        super.onCreate(stateBundle);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        File external = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        modelDir = new File(external == null ? getFilesDir() : external, "CantaLocal");
        downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        prefs = getSharedPreferences("descargas-verificadas", Context.MODE_PRIVATE);
        createSetupPage();
        File binary = new File(getApplicationInfo().nativeLibraryDir, "libace-server.so");
        if (!binary.isFile()) {
            showStatus("Falta el motor ARM64", "Esta compilación no tiene el motor ACE-Step. No se descargarán modelos innecesariamente.", 0);
            setupButton.setEnabled(false);
            return;
        }
        showStatus("CantaLocal está preparado", "Pulsa una vez para descargar los cuatro modelos. Android conservará las descargas incluso si cierras CantaLocal.", 0);
        executor.execute(() -> {
            if (allTrusted()) {
                showStatus("Modelos encontrados", "Arrancando la IA musical guardada...", 95);
                try { startEngine(); } catch (Exception e) { showError(e); }
            }
        });
    }

    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density + .5f); }
    private TextView label(String txt, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(txt); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color);
        d.setCornerRadius(dp(radius)); return d;
    }
    private void createSetupPage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true); scroll.setBackgroundColor(Color.rgb(15,13,27));
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(26), dp(55), dp(26), dp(35));
        scroll.addView(outer);
        TextView icon = label("♫", 64, 0xffc692ff, true);
        outer.addView(icon);
        TextView name = label("CantaLocal", 37, Color.WHITE, true);
        outer.addView(name);
        TextView sub = label("Tu estudio musical con IA, dentro de tu teléfono.", 17, 0xffbab3ce, false);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1,-2); subParams.topMargin = dp(8);
        outer.addView(sub,subParams);

        setupPage = new LinearLayout(this);
        setupPage.setOrientation(LinearLayout.VERTICAL);
        setupPage.setPadding(dp(20),dp(23),dp(20),dp(23));
        setupPage.setBackground(rounded(0xff28243c, 19));
        LinearLayout.LayoutParams cardParam = new LinearLayout.LayoutParams(-1,-2);cardParam.topMargin=dp(34);
        outer.addView(setupPage,cardParam);
        state = label("Preparando...", 22, Color.WHITE, true);
        setupPage.addView(state);
        description = label("", 15, 0xffd9d0e9, false);
        LinearLayout.LayoutParams descParam = new LinearLayout.LayoutParams(-1,-2);descParam.topMargin=dp(10);
        setupPage.addView(description,descParam);
        progress = new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);progress.setProgressTintList(android.content.res.ColorStateList.valueOf(0xffbe8bf5));
        LinearLayout.LayoutParams progressParam = new LinearLayout.LayoutParams(-1,dp(11));progressParam.topMargin=dp(18);
        setupPage.addView(progress,progressParam);
        setupButton = new Button(this);
        setupButton.setText("Preparar IA musical");
        setupButton.setAllCaps(false);setupButton.setTextColor(0xff20162a);
        setupButton.setBackground(rounded(0xffd7b7ff, 13));
        LinearLayout.LayoutParams bParam = new LinearLayout.LayoutParams(-1,dp(56));bParam.topMargin=dp(24);
        setupPage.addView(setupButton,bParam);
        setupButton.setOnClickListener(v -> prepare());
        TextView info = label("Descarga persistente administrada por Android: puedes cerrar la aplicación, cambiar de pantalla o apagarla. Al volver, recuperarás el progreso guardado.\n\nSolo Wi-Fi para proteger tus datos móviles · Aproximadamente 3,3 GB · Sin Termux.\n\nPrueba inicial recomendada: 30 segundos, voz cantada en español.", 14, 0xffaba4be, false);
        LinearLayout.LayoutParams infoParam = new LinearLayout.LayoutParams(-1,-2); infoParam.topMargin=dp(27);
        outer.addView(info,infoParam);
        setContentView(scroll);
    }
    private void showStatus(String title,String detail,int percent) {
        runOnUiThread(() -> {
            if (state == null) return;
            state.setText(title); description.setText(detail);
            progress.setProgress(Math.min(100,Math.max(0,percent)));
        });
    }
    private void setBusy(boolean active) {
        runOnUiThread(() -> {
            if (setupButton == null) return;
            setupButton.setEnabled(!active);
            setupButton.setText(active ? "Preparando IA musical…" : "Preparar IA musical");
            setupButton.setAlpha(active ? 0.72f : 1.0f);
        });
    }
    private void showError(Exception e) {
        showStatus("No se pudo completar", (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()) + "\nPuedes pulsar el botón para reintentar; las descargas anteriores se conservan.", 0);
        setBusy(false);
        synchronized(lock){working=false;}
    }
    // Encola las cuatro transferencias en el servicio de descargas de Android.
    // Este servicio sobrevive al cierre de CantaLocal y a un reinicio del teléfono.
    private void prepare() {
        showStatus("Iniciando descargas persistentes", "Android guardará el progreso y las descargas seguirán en segundo plano con Wi-Fi.", 1);
        setBusy(true);
        executor.execute(() -> {
            try {
                if (getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) == null)
                    throw new Exception("El almacenamiento externo de la aplicación no está disponible.");
                if (!modelDir.isDirectory() && !modelDir.mkdirs())
                    throw new Exception("No se pudo crear la carpeta persistente de modelos.");
                long expected = 500_000_000L;
                for (int i=0; i<MODELS.length; i++) {
                    if (!isTrusted(i) && downloadStatus(i) != DownloadManager.STATUS_SUCCESSFUL)
                        expected += Long.parseLong(MODELS[i][2]);
                }
                long free = new StatFs(modelDir.getAbsolutePath()).getAvailableBytes();
                if (free < expected) throw new Exception(String.format(Locale.ROOT,
                    "Espacio insuficiente: %.1f GB libres, necesitas al menos %.1f GB para completar las descargas.",
                    free/1e9, expected/1e9));
                for (int i=0; i<MODELS.length; i++) {
                    if (isTrusted(i)) continue;
                    long existingId = downloadId(i);
                    int status = downloadStatus(i);
                    if (existingId > 0 && status != -1 && status != DownloadManager.STATUS_FAILED) continue;
                    if (existingId > 0) downloadManager.remove(existingId);
                    File target = model(i);
                    if (target.exists() && !target.delete())
                        throw new Exception("No se puede eliminar una descarga dañada: " + target.getName());
                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(BASE + MODELS[i][0] + "?download=true"));
                    request.setTitle("CantaLocal · modelo " + (i+1) + " de 4");
                    request.setDescription("IA musical sin conexión · se conserva el progreso");
                    request.setMimeType("application/octet-stream");
                    request.setAllowedOverMetered(false);
                    request.setAllowedOverRoaming(false);
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
                    request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, "CantaLocal/" + MODELS[i][0]);
                    long id = downloadManager.enqueue(request);
                    if (!prefs.edit().putLong("download-id-" + MODELS[i][0], id).commit())
                        throw new Exception("No se pudo guardar el identificador de descarga del modelo "+(i+1));
                }
                showStatus("Descargas activadas", "Puedes salir de CantaLocal. Android continuará automáticamente por Wi-Fi y mostrará el progreso en sus notificaciones.", 2);
            } catch (Exception e) {
                showError(e);
            } finally {
                setBusy(false);
            }
        });
    }

    private long downloadId(int i) {
        return prefs.getLong("download-id-" + MODELS[i][0], -1L);
    }

    private int downloadStatus(int i) {
        long id = downloadId(i);
        if (id < 0) return -1;
        try (Cursor c = downloadManager.query(new DownloadManager.Query().setFilterById(id))) {
            if (c == null || !c.moveToFirst()) return -1;
            return c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
        } catch (Exception e) { return -1; }
    }

    private String pausedReason(int reason) {
        if (reason == DownloadManager.PAUSED_WAITING_FOR_NETWORK || reason == DownloadManager.PAUSED_QUEUED_FOR_WIFI)
            return "Esperando Wi-Fi; se reanudará automáticamente";
        if (reason == DownloadManager.PAUSED_WAITING_TO_RETRY)
            return "Conexión inestable; Android intentará continuar automáticamente";
        return "Descarga temporalmente en pausa";
    }

    // Relee siempre las cifras desde la base de datos del sistema, no desde
    // variables de memoria que desaparecen al cerrar la actividad.
    private void refreshDownloads() {
        if (web != null || downloadManager == null || modelDir == null) return;
        long received=0, total=0;
        int known=0, complete=0, failed=0;
        String current="", wait="";
        boolean everySuccess=true;
        for (int i=0; i<MODELS.length; i++) {
            long fallback = Long.parseLong(MODELS[i][2]);
            if (isTrusted(i)) {
                received+=model(i).length();
                total+=model(i).length();
                complete++;
                continue;
            }
            long id=downloadId(i);
            if (id<0) { everySuccess=false; total+=fallback; continue; }
            known++;
            try (Cursor c=downloadManager.query(new DownloadManager.Query().setFilterById(id))) {
                if (c==null || !c.moveToFirst()) {
                    everySuccess=false;failed++;total+=fallback;current="Se perdió el registro de una descarga";
                    continue;
                }
                int status=c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                int reason=c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
                long bytes=Math.max(0,c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)));
                long size=c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                if(size<=0)size=fallback;
                total+=size;received+=Math.min(bytes,size);
                if (status==DownloadManager.STATUS_SUCCESSFUL) {
                    complete++;
                    received += Math.max(0,size-Math.min(bytes,size));
                } else {
                    everySuccess=false;
                    if(status==DownloadManager.STATUS_FAILED) {
                        failed++;
                        current="Error en modelo " + (i+1) + " (código Android " + reason + ")";
                    } else if(status==DownloadManager.STATUS_PAUSED) {
                        wait=pausedReason(reason);
                        current="Modelo " + (i+1) + "/4 · " + (bytes/1_000_000) + " / " + (size/1_000_000) + " MB";
                    } else if(status==DownloadManager.STATUS_RUNNING) {
                        current="Modelo " + (i+1) + "/4 · " + (bytes/1_000_000) + " / " + (size/1_000_000) + " MB";
                    } else if(current.isEmpty()) {
                        current="Modelo " + (i+1) + "/4 en cola de descargas";
                    }
                }
            } catch (Exception e) {
                everySuccess=false;
                failed++;
                current="No se pudo consultar Android: "+e.getMessage();
                total+=fallback;
            }
        }
        if (known==0 && complete==0) return;
        int percentage=(int)(100.0*received/Math.max(1,total));
        if (complete==4) {
            showStatus("Modelos descargados", "Verificando los archivos para iniciar la IA…", 98);
            if (!verifying && !launching) verifyAndOpen();
            return;
        }
        if (failed>0) {
            showStatus("Requiere reintento", current+"\nPulsa «Preparar IA musical» para volver a descargar solo el modelo que falló; los demás se conservan.", percentage);
        } else {
            String title=wait.isEmpty()?"Descargando con Android":"Descarga en pausa automática";
            String detail=String.format(Locale.ROOT,
                "%.0f MB guardados · %d de 4 modelos finalizados\n%s%s\nPuedes cerrar CantaLocal; Android conserva las descargas.",
                received/1e6, complete, current, wait.isEmpty()?"":"\n"+wait);
            showStatus(title, detail, Math.min(97,percentage));
        }
    }

    private void verifyAndOpen() {
        synchronized(lock) {
            if(verifying || launching || web!=null)return;
            verifying=true;
        }
        executor.execute(() -> {
            try {
                for(int i=0;i<MODELS.length;i++){
                    if(isTrusted(i))continue;
                    if(downloadStatus(i)!=DownloadManager.STATUS_SUCCESSFUL)
                        throw new Exception("El modelo "+(i+1)+" todavía no termina de descargarse.");
                    File f=model(i);
                    if(!f.isFile() || f.length()<Long.parseLong(MODELS[i][2]))
                        throw new Exception("El modelo "+(i+1)+" quedó incompleto.");
                    showStatus("Verificando modelo "+(i+1)+"/4", "Revisando integridad SHA-256 de "+f.getName(), 98);
                    if(!sha256(f).equalsIgnoreCase(MODELS[i][1])) {
                        long id=downloadId(i);
                        if(id>0)downloadManager.remove(id);
                        prefs.edit().remove("download-id-"+MODELS[i][0]).remove(MODELS[i][0]).commit();
                        f.delete();
                        throw new Exception("El modelo "+(i+1)+" no pasó la comprobación SHA-256; pulsa Preparar para reintentarlo.");
                    }
                    prefs.edit().putString(MODELS[i][0], signature(f,i)).commit();
                }
                synchronized(lock){launching=true;}
                startEngine();
            } catch(Exception e){ showError(e); }
            finally { synchronized(lock){verifying=false;launching=false;} }
        });
    }

    @Override protected void onResume() {
        super.onResume();
        statusHandler.removeCallbacks(statusTick);
        statusHandler.post(statusTick);
    }
    @Override protected void onPause() {
        statusHandler.removeCallbacks(statusTick);
        super.onPause();
    }

    private boolean allTrusted() { for(int i=0;i<MODELS.length;i++)if(!isTrusted(i))return false;return true; }
    private File model(int i){return new File(modelDir,MODELS[i][0]);}
    private String signature(File f, int i) {return f.length()+":"+f.lastModified()+":"+MODELS[i][1];}
    private boolean isTrusted(int i) {
        File f=model(i);
        return f.isFile() && f.length()>=Long.parseLong(MODELS[i][2]) &&
               signature(f,i).equals(prefs.getString(MODELS[i][0],""));
    }
    private String sha256(File f) throws Exception {
        MessageDigest md=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new BufferedInputStream(new FileInputStream(f),256*1024)) {
            byte[] buf=new byte[256*1024]; int n;
            while((n=in.read(buf))!=-1)md.update(buf,0,n);
        }
        StringBuilder s=new StringBuilder();
        for(byte b:md.digest())s.append(String.format(Locale.ROOT,"%02x",b&0xff));
        return s.toString();
    }
    private boolean health() {
        HttpURLConnection c=null;
        try{
            c=(HttpURLConnection)new URL(HOST+"/health").openConnection();
            c.setConnectTimeout(1500);c.setReadTimeout(1500);
            if(c.getResponseCode()!=200)return false;
            try(InputStream in=c.getInputStream()){
                byte[] b=new byte[100];
                int n=in.read(b);
                return n>0 && new String(b,0,n,"UTF-8").contains("ok");
            }
        }catch(Exception ex){return false;}
        finally {if(c!=null)c.disconnect();}
    }
    private void startEngine() throws Exception {
        showStatus("Iniciando motor musical","Cargando ACE-Step. La primera ejecución puede ser más lenta.",98);
        File exe=new File(getApplicationInfo().nativeLibraryDir,"libace-server.so");
        if(!exe.canExecute())throw new Exception("Android impide ejecutar el motor ARM64 de esta APK.");
        if(!health()) {
            ProcessBuilder pb=new ProcessBuilder(exe.getAbsolutePath(),
                "--models",modelDir.getAbsolutePath(),"--host","127.0.0.1","--port",Integer.toString(PORT),
                "--device","CPU","--max-batch","1","--max-seq","4096","--vae-chunk","128","--vae-overlap","32");
            pb.directory(getFilesDir());
            pb.environment().put("HOME",getFilesDir().getAbsolutePath());
            pb.environment().put("LD_LIBRARY_PATH",getApplicationInfo().nativeLibraryDir);
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(new File(getFilesDir(),"motor.log")));
            aceProcess=pb.start();
            for(int n=0;n<80;n++){
                if(health())break;
                if(!aceProcess.isAlive())throw new Exception("El motor se cerró al iniciar. Es posible que no sea compatible con este teléfono.");
                Thread.sleep(1500);
            }
        }
        if(!health())throw new Exception("El motor no respondió después de arrancar.");
        showStatus("¡IA musical preparada!","Abriendo el estudio para crear canciones con letras cantadas.",100);
        runOnUiThread(() -> openStudio());
    }
    private void openStudio() {
        if(web!=null)return;
        web=new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, android.webkit.WebResourceRequest r) {
                String s=r.getUrl().toString();
                return !s.startsWith(HOST+"/");
            }
        });
        setContentView(web);
        web.loadUrl(HOST+"/");
    }
    @Override public void onBackPressed() {
        if(web!=null&&web.canGoBack())web.goBack();
        else if(web!=null){web.destroy();web=null;createSetupPage();showStatus("IA preparada","Puedes volver a abrir el estudio.",100);setupButton.setText("Abrir estudio");setupButton.setOnClickListener(v->openStudio());}
        else super.onBackPressed();
    }
    @Override protected void onDestroy(){
        if(web!=null){web.destroy();web=null;}
        if(aceProcess!=null)aceProcess.destroy();
        statusHandler.removeCallbacks(statusTick);
        executor.shutdownNow();
        super.onDestroy();
    }
}
