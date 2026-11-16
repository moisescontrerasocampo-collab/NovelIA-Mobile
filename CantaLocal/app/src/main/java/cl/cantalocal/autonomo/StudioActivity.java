package cl.cantalocal.autonomo;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StudioActivity extends Activity {
    private static final String HOST = "http://127.0.0.1:8085";
    private static final int BG = 0xff110d1d, CARD = 0xff262037, INPUT = 0xff342c49;
    private static final int LAVENDER = 0xffd3aaff, MUTED = 0xffbcb0d0, WHITE=0xffffffff;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private SharedPreferences drafts;
    private EditText titleBox, styleBox, lyricsBox;
    private RadioGroup durationGroup, voiceGroup;
    private TextView message, progressInfo, songTitle;
    private ProgressBar busyProgress;
    private Button generate, cancelButton, exportButton, playbackButton;
    private LinearLayout songsContainer, resultCard;
    private volatile String currentJob = "";
    private volatile boolean cancelled = false, busy = false;
    private File activeSong;
    private MediaPlayer mediaPlayer;
    private int selectedDuration = 30;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        drafts = getSharedPreferences("studio_es_draft",MODE_PRIVATE);
        buildScreen();
        listSongs();
        showMessage("IA musical lista. Escribe tu letra y el estilo que imaginas.",false);
    }
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable shape(int color,int radius){
        GradientDrawable g=new GradientDrawable();
        g.setColor(color);g.setCornerRadius(dp(radius));return g;
    }
    private TextView text(String s,int sp,int color,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private LinearLayout vertical(){
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;
    }
    private void add(LinearLayout parent,View v,int top){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);
        parent.addView(v,p);
    }
    private void buildScreen(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        LinearLayout root=vertical();root.setPadding(dp(20),dp(26),dp(20),dp(36));scroll.addView(root);
        TextView mark=text("♫  CantaLocal",27,LAVENDER,true);add(root,mark,0);
        add(root,text("Tu estudio de canciones con inteligencia artificial",14,MUTED,false),6);

        LinearLayout form=vertical();form.setPadding(dp(18),dp(19),dp(18),dp(21));
        form.setBackground(shape(CARD,19));add(root,form,24);
        add(form,text("Crea una canción",22,WHITE,true),0);
        add(form,text("Escribe lo que quieres escuchar. Lo demás lo prepara la IA.",14,MUTED,false),6);

        titleBox=field(form,"Título","Nombre de tu canción",drafts.getString("title",""),false,12);
        lyricsBox=field(form,"Letra","Escribe aquí la letra que quieres que canten…\n\n[Verso]\n…\n\n[Coro]\n…",
                        drafts.getString("lyrics",""),true,16);
        lyricsBox.setMinLines(6);lyricsBox.setGravity(Gravity.TOP|Gravity.START);
        styleBox=field(form,"Estilo musical","Por ejemplo: rock alternativo suave, voz masculina emotiva, guitarras y piano",
                         drafts.getString("style",""),true,16);
        styleBox.setMinLines(3);styleBox.setGravity(Gravity.TOP|Gravity.START);

        add(form,text("Duración de la prueba",14,WHITE,true),16);
        durationGroup=new RadioGroup(this);durationGroup.setOrientation(RadioGroup.HORIZONTAL);
        addOption(durationGroup,"30 segundos",30,drafts.getInt("duration",30)==30);
        addOption(durationGroup,"1 minuto",60,drafts.getInt("duration",30)==60);
        add(form,durationGroup,4);

        add(form,text("Tipo de voz",14,WHITE,true),14);
        voiceGroup=new RadioGroup(this);voiceGroup.setOrientation(RadioGroup.VERTICAL);
        int savedVoice=drafts.getInt("voice",0);
        addOption(voiceGroup,"Según el estilo que escribas",0,savedVoice==0);
        addOption(voiceGroup,"Voz masculina",1,savedVoice==1);
        addOption(voiceGroup,"Voz femenina",2,savedVoice==2);
        add(form,voiceGroup,4);

        generate=button("♫  Generar canción",true);
        generate.setOnClickListener(v -> generateSong());
        add(form,generate,22);

        busyProgress=new ProgressBar(this);busyProgress.setIndeterminate(true);
        busyProgress.setIndeterminateTintList(ColorStateList.valueOf(LAVENDER));
        busyProgress.setVisibility(View.GONE);
        LinearLayout.LayoutParams spinP=new LinearLayout.LayoutParams(dp(36),dp(36));spinP.gravity=Gravity.CENTER_HORIZONTAL;spinP.topMargin=dp(16);
        form.addView(busyProgress,spinP);
        message=text("",14,MUTED,false);message.setGravity(Gravity.CENTER_HORIZONTAL);add(form,message,12);
        cancelButton=button("Cancelar creación",false);cancelButton.setVisibility(View.GONE);
        cancelButton.setOnClickListener(v -> cancelGeneration());add(form,cancelButton,12);
        add(form,text("La generación se hace dentro de tu teléfono. En el HONOR puede consumir bastante memoria y tardar, especialmente la primera vez.",12,MUTED,false),14);

        resultCard=vertical();resultCard.setPadding(dp(17),dp(17),dp(17),dp(17));
        resultCard.setBackground(shape(CARD,19));resultCard.setVisibility(View.GONE);add(root,resultCard,18);
        add(resultCard,text("¡Tu canción está lista!",20,WHITE,true),0);
        songTitle=text("Canción",16,LAVENDER,true);add(resultCard,songTitle,8);
        playbackButton=button("▶  Escuchar canción",true);
        playbackButton.setOnClickListener(v->togglePlay(activeSong));add(resultCard,playbackButton,12);
        exportButton=button("↓  Guardar MP3 en Música",false);
        exportButton.setOnClickListener(v->exportSong(activeSong));add(resultCard,exportButton,8);

        add(root,text("Mis canciones",21,WHITE,true),26);
        add(root,text("Tus canciones quedan guardadas en esta aplicación.",13,MUTED,false),5);
        songsContainer=vertical();add(root,songsContainer,12);
        setContentView(scroll);
    }
    private EditText field(LinearLayout parent,String name,String hint,String value,boolean multiline,int top){
        add(parent,text(name,14,WHITE,true),top);
        EditText box=new EditText(this);
        box.setTextSize(16);box.setTextColor(WHITE);box.setHintTextColor(0xff9689ac);
        box.setHint(hint);box.setText(value);
        box.setBackground(shape(INPUT,12));box.setPadding(dp(14),dp(12),dp(14),dp(12));
        box.setSingleLine(!multiline);
        if(multiline)box.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        add(parent,box,7);
        box.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){saveDraft();}
            @Override public void afterTextChanged(Editable e){}
        });
        return box;
    }
    private void addOption(RadioGroup group,String text,int id,boolean checked){
        RadioButton b=new RadioButton(this);
        b.setText(text);b.setTextSize(14);b.setTextColor(WHITE);
        b.setButtonTintList(ColorStateList.valueOf(LAVENDER));
        b.setId(100+id);
        group.addView(b);
        b.setChecked(checked);
        group.setOnCheckedChangeListener((g,i)->saveDraft());
    }
    private Button button(String s,boolean accent){
        Button b=new Button(this);b.setText(s);b.setTextSize(15);b.setAllCaps(false);
        b.setTextColor(accent?0xff20152f:WHITE);b.setBackground(shape(accent?LAVENDER:INPUT,13));
        b.setMinHeight(dp(51));
        return b;
    }
    private void saveDraft(){
        if(titleBox==null||styleBox==null||lyricsBox==null||durationGroup==null||voiceGroup==null)return;
        drafts.edit().putString("title",titleBox.getText().toString())
          .putString("lyrics",lyricsBox.getText().toString())
          .putString("style",styleBox.getText().toString())
          .putInt("duration",durationGroup.getCheckedRadioButtonId()==160?60:30)
          .putInt("voice",Math.max(0,voiceGroup.getCheckedRadioButtonId()-100)).apply();
    }
    private void showMessage(String s,boolean working){
        main.post(()->{message.setText(s);busyProgress.setVisibility(working?View.VISIBLE:View.GONE);
            generate.setEnabled(!working);generate.setAlpha(working?.65f:1f);
            cancelButton.setVisibility(working?View.VISIBLE:View.GONE);});
    }
    private void generateSong(){
        if(busy)return;
        String title=titleBox.getText().toString().trim();
        String lyrics=lyricsBox.getText().toString().trim();
        String style=styleBox.getText().toString().trim();
        if(title.isEmpty()){titleBox.setError("Escribe el título");titleBox.requestFocus();return;}
        if(lyrics.isEmpty()){lyricsBox.setError("Escribe la letra que quieres escuchar");lyricsBox.requestFocus();return;}
        if(style.isEmpty()){styleBox.setError("Indica el estilo musical");styleBox.requestFocus();return;}
        int duration=durationGroup.getCheckedRadioButtonId()==160?60:30;
        int voice=Math.max(0,voiceGroup.getCheckedRadioButtonId()-100);
        String caption=style;
        if(voice==1)caption += ", voz masculina cantando en español";
        else if(voice==2)caption += ", voz femenina cantando en español";
        else caption += ", interpretación vocal cantada en español";
        saveDraft();
        cancelled=false;busy=true;currentJob="";
        showMessage("Interpretando tu letra y preparando la música…",true);
        String finalCaption=caption;
        executor.execute(()->{
            try{
                JSONObject request=new JSONObject();
                request.put("caption",finalCaption);
                request.put("lyrics",lyrics);
                request.put("duration",duration);
                request.put("vocal_language","Spanish");
                request.put("task_type","text2music");
                request.put("synth_batch_size",1);
                request.put("lm_batch_size",1);
                request.put("lm_model","acestep-5Hz-lm-0.6B-Q8_0.gguf");
                request.put("synth_model","acestep-v15-turbo-Q4_K_M.gguf");
                request.put("output_format","mp3");
                request.put("inference_steps",8);
                // El primer paso escribe los códigos de audio; el segundo sintetiza voz e instrumentos.
                JSONObject prepared=request;
                boolean lmOk=false;
                try{
                    JSONObject lmRequest=new JSONObject(request.toString());
                    lmRequest.put("lm_mode","generate");
                    currentJob=postJob("/lm",lmRequest);
                    waitForJob(currentJob,"Interpretando tu letra");
                    JSONArray results=new JSONArray(getText("/job?id="+currentJob+"&result=1"));
                    if(results.length()>0) {
                        prepared=results.getJSONObject(0);
                        // Nunca sustituir la letra original por una letra inventada.
                        prepared.put("lyrics",lyrics);
                        prepared.put("caption",finalCaption);
                        lmOk=true;
                    }
                }catch(Exception lmErr){
                    if(cancelled)throw lmErr;
                    showMessage("La primera etapa no se completó. Probando creación directa de la canción…",true);
                }
                if(cancelled)throw new Exception("Creación cancelada");
                prepared.put("task_type","text2music");
                prepared.put("output_format","mp3");
                prepared.put("duration",duration);
                prepared.put("synth_batch_size",1);
                prepared.put("synth_model","acestep-v15-turbo-Q4_K_M.gguf");
                prepared.put("inference_steps",8);
                currentJob=postJob("/synth",prepared);
                waitForJob(currentJob, lmOk?"Creando la voz y los instrumentos":"Generando la canción");
                if(cancelled)throw new Exception("Creación cancelada");
                showMessage("Guardando tu canción…",true);
                byte[] audio=extractMp3(currentJob);
                if(audio.length<1024)throw new Exception("El motor no devolvió un archivo de audio válido.");
                File file=writeSong(title,audio);
                main.post(()->{
                    activeSong=file;resultCard.setVisibility(View.VISIBLE);songTitle.setText(title);
                    playbackButton.setText("▶  Escuchar canción");
                    listSongs();
                    showMessage("¡Terminada! Puedes escucharla y guardar el MP3.",false);
                });
            }catch(Exception e){
                String reason=e.getMessage()==null?"No fue posible generar la canción":e.getMessage();
                showMessage(cancelled?"Creación cancelada.":friendlyError(reason),false);
            }finally{busy=false;currentJob="";}
        });
    }
    private String friendlyError(String err){
        if(err.contains("failed")||err.contains("Generation failed"))
            return "La IA no consiguió terminar esta canción. Prueba con 30 segundos y una letra más corta.";
        if(err.contains("Connection refused")||err.contains("connect"))
            return "Se perdió la conexión con el motor local. Vuelve a abrir CantaLocal y prueba de nuevo.";
        if(err.contains("memory")||err.contains("alloc"))
            return "No alcanzó la memoria del teléfono. Cierra otras aplicaciones y prueba 30 segundos.";
        return "No se pudo generar: "+err;
    }
    private void cancelGeneration(){
        cancelled=true;
        String id=currentJob;
        showMessage("Cancelando la creación…",true);
        executor.execute(()->{
            if(id!=null&&!id.isEmpty())try{request("POST","/job?id="+id+"&cancel=1",null);}catch(Exception ignored){}
        });
    }
    private String postJob(String path,JSONObject obj)throws Exception{
        byte[] bytes=request("POST",path,obj.toString().getBytes(StandardCharsets.UTF_8));
        JSONObject res=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
        String id=res.optString("id","");
        if(id.isEmpty())throw new IOException("El motor no confirmó el inicio de la creación.");
        return id;
    }
    private void waitForJob(String id,String label)throws Exception{
        int attempts=0;
        for(;;){
            if(cancelled){try{request("POST","/job?id="+id+"&cancel=1",null);}catch(Exception ignored){}throw new Exception("Creación cancelada");}
            String status=new JSONObject(getText("/job?id="+id)).optString("status","");
            if("done".equals(status))return;
            if("failed".equals(status))throw new Exception("Generation failed");
            if("cancelled".equals(status))throw new Exception("Creación cancelada");
            if(attempts++%5==0)showMessage(label+"…\nLa IA está trabajando dentro del teléfono. Puedes mantener abierta esta pantalla.",true);
            Thread.sleep(2000);
        }
    }
    private String getText(String path)throws Exception{return new String(request("GET",path,null),StandardCharsets.UTF_8);}
    private byte[] request(String method,String path,byte[] payload)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(HOST+path).openConnection();
        try{
            c.setRequestMethod(method);c.setConnectTimeout(5000);c.setReadTimeout(600000);
            if(payload!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");
                try(OutputStream out=c.getOutputStream()){out.write(payload);}}
            int code=c.getResponseCode();
            InputStream in=code>=200&&code<300?c.getInputStream():c.getErrorStream();
            String errorText="";
            if(in!=null) {byte[] data=readAll(in);if(code>=200&&code<300)return data;
                errorText=new String(data,StandardCharsets.UTF_8);}
            throw new IOException("HTTP "+code+" "+(errorText.length()>180?errorText.substring(0,180):errorText));
        }finally{c.disconnect();}
    }
    private byte[] readAll(InputStream input)throws IOException{
        try(InputStream in=new BufferedInputStream(input);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buf=new byte[32768];int n;
            while((n=in.read(buf))!=-1)out.write(buf,0,n);
            return out.toByteArray();
        }
    }
    private static int find(byte[] data,byte[] needle,int from){
        outer:for(int i=from;i<=data.length-needle.length;i++){
            for(int j=0;j<needle.length;j++)if(data[i+j]!=needle[j])continue outer;
            return i;
        }
        return -1;
    }
    private byte[] extractMp3(String job)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(HOST+"/job?id="+job+"&result=1").openConnection();
        try{
            c.setConnectTimeout(5000);c.setReadTimeout(600000);
            if(c.getResponseCode()!=200)throw new IOException("No se pudo recuperar el audio");
            String type=c.getHeaderField("Content-Type");
            byte[] bytes=readAll(c.getInputStream());
            if(type==null||!type.toLowerCase(Locale.ROOT).startsWith("multipart/"))return bytes;
            int bi=type.indexOf("boundary=");if(bi<0)throw new IOException("Formato de audio incompleto");
            String boundary=type.substring(bi+9).split(";")[0].replace("\"","").trim();
            byte[] delimiter=("\r\n--"+boundary).getBytes(StandardCharsets.ISO_8859_1);
            byte[] eol=("\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1);
            byte[] start=("--"+boundary).getBytes(StandardCharsets.ISO_8859_1);
            int pos=find(bytes,start,0);
            while(pos>=0){
                int headerEnd=find(bytes,eol,pos);
                if(headerEnd<0)break;
                String header=new String(bytes,pos,headerEnd-pos,StandardCharsets.ISO_8859_1).toLowerCase(Locale.ROOT);
                int bodyStart=headerEnd+4;
                int next=find(bytes,delimiter,bodyStart);
                if(next<0)break;
                if(header.contains("audio/mpeg")||header.contains("audio/mp3")){
                    return Arrays.copyOfRange(bytes,bodyStart,next);
                }
                pos=next+2;
            }
            throw new IOException("La respuesta del motor no contenía audio MP3.");
        }finally{c.disconnect();}
    }
    private File songFolder(){
        File base=getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        File dir=new File(base!=null?base:getFilesDir(),"CantaLocal");
        if(!dir.exists())dir.mkdirs();return dir;
    }
    private File writeSong(String title,byte[] bytes)throws Exception{
        String safe=title.replaceAll("[^\\p{L}\\p{N} _-]","").trim();
        if(safe.length()>48)safe=safe.substring(0,48);
        if(safe.isEmpty())safe="Mi canción";
        String stamp=new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.ROOT).format(new Date());
        File f=new File(songFolder(),safe+"_"+stamp+".mp3");
        try(FileOutputStream out=new FileOutputStream(f)){out.write(bytes);out.getFD().sync();}
        return f;
    }
    private void listSongs(){
        if(songsContainer==null)return;
        songsContainer.removeAllViews();
        File[] tracks=songFolder().listFiles((d,n)->n.toLowerCase(Locale.ROOT).endsWith(".mp3"));
        if(tracks==null||tracks.length==0){
            add(songsContainer,text("Todavía no hay canciones. La primera que generes aparecerá aquí.",13,MUTED,false),5);
            return;
        }
        Arrays.sort(tracks,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
        for(File file:tracks){
            LinearLayout item=vertical();item.setPadding(dp(15),dp(14),dp(15),dp(15));
            item.setBackground(shape(CARD,15));add(songsContainer,item,8);
            add(item,text(file.getName().replaceAll("_[0-9]{8}_[0-9]{6}\\.mp3$",""),16,WHITE,true),0);
            add(item,text(String.format(Locale.ROOT,"%.1f MB · MP3 guardado",file.length()/1000000.0),12,MUTED,false),4);
            LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);
            Button listen=button("▶ Escuchar",true);listen.setTextSize(13);
            listen.setOnClickListener(v->togglePlay(file));
            Button save=button("↓ Exportar",false);save.setTextSize(13);
            save.setOnClickListener(v->exportSong(file));
            actions.addView(listen,new LinearLayout.LayoutParams(0,dp(45),1));
            LinearLayout.LayoutParams gap=new LinearLayout.LayoutParams(0,dp(45),1);
            gap.leftMargin=dp(8);actions.addView(save,gap);
            add(item,actions,10);
        }
    }
    private void togglePlay(File song){
        if(song==null||!song.exists()){Toast.makeText(this,"No se encontró el MP3",Toast.LENGTH_SHORT).show();return;}
        try {
            if(mediaPlayer!=null){mediaPlayer.stop();mediaPlayer.release();mediaPlayer=null;}
            mediaPlayer=new MediaPlayer();
            mediaPlayer.setDataSource(song.getAbsolutePath());
            mediaPlayer.prepare();
            mediaPlayer.start();
            activeSong=song;
            playbackButton.setText("▶  Escuchar otra vez");
            Toast.makeText(this,"Reproduciendo "+song.getName(),Toast.LENGTH_SHORT).show();
            mediaPlayer.setOnCompletionListener(p->{p.release();if(mediaPlayer==p)mediaPlayer=null;});
        }catch(Exception e){Toast.makeText(this,"No se pudo reproducir el audio",Toast.LENGTH_LONG).show();}
    }
    private void exportSong(File song){
        if(song==null||!song.isFile()){Toast.makeText(this,"Primero genera una canción",Toast.LENGTH_SHORT).show();return;}
        if(Build.VERSION.SDK_INT<29){Toast.makeText(this,"La exportación requiere Android 10 o posterior",Toast.LENGTH_LONG).show();return;}
        executor.execute(()->{
            Uri output=null;
            try{
                ContentValues values=new ContentValues();
                values.put(MediaStore.Audio.Media.DISPLAY_NAME,song.getName());
                values.put(MediaStore.Audio.Media.MIME_TYPE,"audio/mpeg");
                values.put(MediaStore.Audio.Media.RELATIVE_PATH,Environment.DIRECTORY_MUSIC+"/CantaLocal");
                values.put(MediaStore.Audio.Media.IS_PENDING,1);
                output=getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,values);
                if(output==null)throw new Exception("Android no permitió crear el MP3.");
                try(InputStream in=new FileInputStream(song);OutputStream out=getContentResolver().openOutputStream(output)){
                    if(out==null)throw new Exception("No se pudo abrir el archivo de destino.");
                    byte[] buf=new byte[65536];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);
                }
                values.clear();values.put(MediaStore.Audio.Media.IS_PENDING,0);
                getContentResolver().update(output,values,null,null);
                main.post(()->Toast.makeText(this,"Guardada en Música / CantaLocal",Toast.LENGTH_LONG).show());
            }catch(Exception ex){
                Uri bad=output;if(bad!=null)getContentResolver().delete(bad,null,null);
                main.post(()->Toast.makeText(this,"No se pudo guardar el MP3: "+ex.getMessage(),Toast.LENGTH_LONG).show());
            }
        });
    }
    @Override protected void onPause(){saveDraft();super.onPause();}
    @Override protected void onDestroy(){
        cancelled=true;
        executor.shutdownNow();
        if(mediaPlayer!=null){mediaPlayer.release();mediaPlayer=null;}
        super.onDestroy();
    }
}
