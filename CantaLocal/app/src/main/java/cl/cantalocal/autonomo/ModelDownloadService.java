package cl.cantalocal.autonomo;

import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.StatFs;
import android.os.SystemClock;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;

public class ModelDownloadService extends Service {
    public static final String ACTION_START = "cl.cantalocal.autonomo.DOWNLOAD_START";
    public static final String ACTION_PAUSE = "cl.cantalocal.autonomo.DOWNLOAD_PAUSE";
    private static final String CHANNEL = "cantalocal_download_v1";
    private static final int NOTIFY_ID = 5041;
    private static final String BASE = "https://huggingface.co/Serveurperso/ACE-Step-1.5-GGUF/resolve/83f32e465c3abd605271d0c1c935ad2faf28c197/";
    private static final String[][] MODELS = {
      {"acestep-5Hz-lm-0.6B-Q8_0.gguf","bdaf9e292d4470f31c19cafeaca1b74936a114667e3a85e5d33b65247e9908ec","650000000"},
      {"acestep-v15-turbo-Q4_K_M.gguf","55b4d8514850f3d0f82536f37e99673aaf48df802b5ae5b153eea32a2e2daa5e","1300000000"},
      {"Qwen3-Embedding-0.6B-Q8_0.gguf","972f23255e46adfe744a0eb9a0039f3c63988f65753b0968d776e8b27168c321","700000000"},
      {"vae-BF16.gguf","0599862ac5d15cd308e1d2e368373aea6c02e25ebd1737ad4a4562a0901b0ef8","310000000"}
    };
    private NotificationManager notifications;
    private SharedPreferences prefs;
    private File dir;
    private volatile boolean stopped=false;
    private volatile boolean running=false;
    private Thread worker;
    private PowerManager.WakeLock cpu;
    private WifiManager.WifiLock wifi;
    private long lastNotice=0;

    @Override public void onCreate(){
        super.onCreate();
        notifications=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        notifications.createNotificationChannel(new NotificationChannel(CHANNEL,"Descarga de modelos de CantaLocal",NotificationManager.IMPORTANCE_LOW));
        prefs=getSharedPreferences("descargas-verificadas",Context.MODE_PRIVATE);
        File base=getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        dir=new File(base!=null?base:getFilesDir(),"CantaLocal");
    }
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?ACTION_START:intent.getAction();
        if(ACTION_PAUSE.equals(action)) {
            stopped=true;
            state("pausado","Descarga pausada. El progreso permanece guardado.",progress());
            if(worker!=null)worker.interrupt();
            // Se detiene al terminar la escritura actual.
            return START_NOT_STICKY;
        }
        if(running)return START_STICKY;
        stopped=false;
        running=true;
        state("iniciando","Activando descarga persistente con pantalla apagada…",progress());
        Notification n=makeNotification("Preparando IA musical","Descarga activa con pantalla apagada",progress(),true);
        try {
            if(Build.VERSION.SDK_INT>=29)
                startForeground(NOTIFY_ID,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            else startForeground(NOTIFY_ID,n);
        }catch(Exception e){
            state("error","Android no permitió iniciar la notificación: "+e.getMessage(),progress());
            running=false;
            stopSelf();
            return START_NOT_STICKY;
        }
        worker=new Thread(this::runDownloads,"CantaLocal-Foreground-Download");
        worker.start();
        return START_STICKY;
    }

    private Notification makeNotification(String title,String detail,int percentage,boolean ongoing){
        Intent open=new Intent(this,MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content=PendingIntent.getActivity(this,14,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Intent stop=new Intent(this,ModelDownloadService.class).setAction(ACTION_PAUSE);
        PendingIntent pause=PendingIntent.getService(this,15,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        b.setContentTitle(title).setContentText(detail).setSmallIcon(android.R.drawable.stat_sys_download)
         .setContentIntent(content).setOnlyAlertOnce(true).setOngoing(ongoing)
         .setWhen(System.currentTimeMillis()).setShowWhen(false);
        if(ongoing) {
            b.setProgress(100,Math.max(0,Math.min(100,percentage)),false);
            b.addAction(android.R.drawable.ic_media_pause,"Pausar",pause);
        } else b.setProgress(0,0,false);
        return b.build();
    }
    private void notice(String title,String detail,int percentage,boolean force){
        long now=SystemClock.elapsedRealtime();
        if(!force && now-lastNotice<700)return;
        lastNotice=now;
        state("descargando",title+" · "+detail,percentage);
        notifications.notify(NOTIFY_ID,makeNotification(title,detail,percentage,true));
    }
    private void state(String status,String message,int percentage){
        prefs.edit().putString("dl-state",status).putString("dl-message",message)
          .putInt("dl-progress",percentage).putLong("dl-update",System.currentTimeMillis()).apply();
    }
    private File model(int i){return new File(dir,MODELS[i][0]);}
    private File part(int i){return new File(dir,MODELS[i][0]+".part");}
    private int progress(){
        long done=0,total=0;
        for(int i=0;i<MODELS.length;i++) {
            long known=Math.max(Long.parseLong(MODELS[i][2]),prefs.getLong("dl-size-"+i,0L));
            total+=known;
            if(isTrusted(i)) done+=known;
            else done+=Math.min(known,part(i).length());
        }
        return (int)Math.min(99,Math.max(0,done*100L/Math.max(1,total)));
    }
    private String signature(File f,int i){
        return f.length()+":"+f.lastModified()+":"+MODELS[i][1];
    }
    private boolean isTrusted(int i){
        File f=model(i);
        return f.isFile() && f.length()>=Long.parseLong(MODELS[i][2]) &&
            signature(f,i).equals(prefs.getString(MODELS[i][0],""));
    }
    private static String sha256(File f) throws Exception {
        MessageDigest d=MessageDigest.getInstance("SHA-256");
        try(FileInputStream in=new FileInputStream(f)){
            byte[] b=new byte[262144];int n;
            while((n=in.read(b))!=-1)d.update(b,0,n);
        }
        StringBuilder s=new StringBuilder();
        for(byte b:d.digest())s.append(String.format(Locale.ROOT,"%02x",b&255));
        return s.toString();
    }
    private void acquirePower(){
        PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
        cpu=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"CantaLocal:MusicaDescargas");
        cpu.setReferenceCounted(false);
        cpu.acquire();
        WifiManager wm=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
        if(wm!=null){
            try {
                wifi=wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF,"CantaLocal:Modelos");
                wifi.setReferenceCounted(false);
                wifi.acquire();
            }catch(Exception ignored){}
        }
    }
    private void releasePower(){
        try{if(wifi!=null&&wifi.isHeld())wifi.release();}catch(Exception ignored){}
        try{if(cpu!=null&&cpu.isHeld())cpu.release();}catch(Exception ignored){}
    }
    private void runDownloads(){
        try {
            if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("No se pudo crear la carpeta de modelos");
            long needed=300_000_000L;
            for(int i=0;i<MODELS.length;i++){
                if(!isTrusted(i))
                    needed+=Math.max(0,Long.parseLong(MODELS[i][2])-part(i).length());
            }
            if(new StatFs(dir.getAbsolutePath()).getAvailableBytes()<needed)
                throw new IOException("Falta espacio libre para completar los modelos.");
            acquirePower();
            for(int i=0;i<MODELS.length;i++){
                if(stopped)break;
                if(isTrusted(i))continue;
                File f=model(i);
                // Reconocer archivos completos del DownloadManager anterior sin descargarlos otra vez.
                if(f.isFile() && f.length()>=Long.parseLong(MODELS[i][2]) &&
                        sha256(f).equalsIgnoreCase(MODELS[i][1])){
                    prefs.edit().putString(MODELS[i][0],signature(f,i)).commit();
                    continue;
                }
                if(f.exists())f.delete();
                boolean ok=false;
                for(int attempt=0; !stopped; attempt++) {
                    try{
                        downloadOne(i);
                        ok=true;
                        break;
                    }catch(Exception ex){
                        if(stopped)break;
                        String message=ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage();
                        notice("Reconectando al servidor","Modelo "+(i+1)+"/4 · "+message,progress(),true);
                        // No se borra .part: se reanuda desde el último byte guardado.
                        for(int j=0;j<Math.min(30,3+attempt*2)&&!stopped;j++){
                            try{Thread.sleep(1000);}catch(InterruptedException interrupted){if(stopped)break;}
                        }
                    }
                }
                if(!ok && !stopped)throw new IOException("No se completó el modelo "+(i+1));
            }
            if(stopped){
                state("pausado","Descarga pausada. Los archivos .part se conservaron.",progress());
                notifications.notify(NOTIFY_ID,makeNotification("CantaLocal en pausa","Pulsa Preparar para reanudar",progress(),false));
            }else{
                prefs.edit().putString("dl-state","completado").putString("dl-message","Cuatro modelos verificados. Abre CantaLocal para generar música.").putInt("dl-progress",100).commit();
                notifications.notify(NOTIFY_ID,makeNotification("IA musical descargada","Modelos listos y verificados. Abre CantaLocal.",100,false));
            }
        }catch(Exception ex){
            String m=ex.getMessage()==null?ex.toString():ex.getMessage();
            state("error",m,progress());
            notifications.notify(NOTIFY_ID,makeNotification("Descarga detenida",m,progress(),false));
        }finally{
            releasePower();
            running=false;
            stopForeground(STOP_FOREGROUND_DETACH);
            stopSelf();
        }
    }
    private void downloadOne(int i)throws Exception {
        File partial=part(i);
        final String name=MODELS[i][0];
        long minimum=Long.parseLong(MODELS[i][2]);
        HttpURLConnection c=null;
        long offset=partial.length();
        try {
            notice("Modelo "+(i+1)+" de 4","Conectando · "+(offset/1_000_000)+" MB guardados",progress(),true);
            URL url=new URL(BASE+name+"?download=true");
            c=(HttpURLConnection)url.openConnection();
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(25000);
            c.setReadTimeout(45000);
            c.setRequestProperty("User-Agent","CantaLocal/0.6");
            c.setRequestProperty("Accept-Encoding","identity");
            if(offset>0)c.setRequestProperty("Range","bytes="+offset+"-");
            int status=c.getResponseCode();
            if(status==416 && offset>=minimum){
                checkPartial(i);
                return;
            }
            if(status!=200 && status!=206)throw new IOException("HTTP "+status);
            boolean append=(status==206 && offset>0);
            if(append){
                String range=c.getHeaderField("Content-Range");
                if(range==null||!range.startsWith("bytes "+offset+"-"))
                    throw new IOException("Respuesta de reanudación incorrecta");
            }else offset=0;
            long remaining=c.getContentLengthLong();
            long total=remaining>0?offset+remaining:minimum;
            prefs.edit().putLong("dl-size-"+i,total).apply();
            long bytes=offset;
            long lastCommit=0;
            long lastShown=0;
            try(InputStream in=new BufferedInputStream(c.getInputStream(),131072);
                FileOutputStream stream=new FileOutputStream(partial,append);
                BufferedOutputStream out=new BufferedOutputStream(stream,131072)){
                byte[] buffer=new byte[131072];
                int n;
                while(!stopped && (n=in.read(buffer))!=-1){
                    out.write(buffer,0,n);
                    bytes+=n;
                    if(bytes-lastCommit>=4_000_000L) {
                        out.flush();
                        stream.getFD().sync();
                        lastCommit=bytes;
                    }
                    if(bytes-lastShown>=500_000L){
                        lastShown=bytes;
                        notice("Descargando modelo "+(i+1)+"/4",
                            String.format(Locale.ROOT,"%.1f / %.1f MB · Total %d%%",
                                bytes/1e6,total/1e6,progress()),progress(),false);
                    }
                }
                out.flush();
                stream.getFD().sync();
            }
            if(stopped)return;
            if(partial.length()<minimum)throw new IOException("Descarga incompleta, intentando continuar");
            checkPartial(i);
        }finally{
            if(c!=null)c.disconnect();
        }
    }
    private void checkPartial(int i)throws Exception{
        File partial=part(i);
        notice("Verificando modelo "+(i+1)+"/4","Comprobando integridad SHA-256",progress(),true);
        if(!sha256(partial).equalsIgnoreCase(MODELS[i][1])){
            // Conservar una copia para análisis, pero nunca tratarla como modelo válido.
            // Si la descarga está completa y mal, reintentar desde cero.
            partial.delete();
            throw new IOException("Archivo incorrecto; descargando de nuevo");
        }
        File dest=model(i);
        if(!partial.renameTo(dest))throw new IOException("No se pudo guardar "+dest.getName());
        if(!prefs.edit().putString(MODELS[i][0],signature(dest,i)).commit())
            throw new IOException("No se pudo guardar la verificación del modelo");
        notice("Modelo "+(i+1)+" completado","Continuando automáticamente con el siguiente",progress(),true);
    }

    @Override public void onDestroy(){
        stopped=true;
        if(worker!=null)worker.interrupt();
        releasePower();
        super.onDestroy();
    }
}
