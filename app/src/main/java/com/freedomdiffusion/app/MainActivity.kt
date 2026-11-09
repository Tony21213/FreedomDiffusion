package com.freedomdiffusion.app

import android.app.*
import android.os.*
import android.content.ContentValues
import android.provider.MediaStore
import android.view.*
import android.widget.*
import java.io.*
import java.net.*
import java.security.MessageDigest
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var state: TextView
    private lateinit var bar: ProgressBar
    private lateinit var prompt: EditText
    private lateinit var preview: ImageView
    private lateinit var load: Button
    private lateinit var generate: Button
    @Volatile private var working = false
    private val model by lazy { File(filesDir, "sd15.safetensors") }
    private val native by lazy { File(applicationInfo.nativeLibraryDir, "libsd.so") }
    private val link = "https://huggingface.co/Comfy-Org/stable-diffusion-1.5/resolve/main/v1-5-pruned-emaonly-fp16.safetensors"
    private val sha = "e9476a13728cd75d8279f6ec8bad753a66a1957ca375a1464dc63b37db6e3916"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation=1; setPadding(28,35,28,25) }
        fun add(v: View) = layout.addView(v, LinearLayout.LayoutParams(-1,-2))
        add(TextView(this).apply { text="Freedom Diffusion"; textSize=27f })
        add(TextView(this).apply { text="Генерация полностью на телефоне • SD 1.5 • CPU" })
        prompt=EditText(this).apply { hint="Опиши изображение на английском"; minLines=3; maxLines=5 }
        add(prompt)
        state=TextView(this).apply { text="Проверка модели…"; setPadding(0,12,0,8) }
        add(state)
        bar=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply { max=100 }
        add(bar)
        load=Button(this).apply { text="Скачать модель один раз (~2,1 ГБ)"; setOnClickListener { confirmDownload() } }
        add(load)
        generate=Button(this).apply { text="Создать изображение 384×384"; setOnClickListener { createImage() } }
        add(generate)
        preview=ImageView(this).apply { adjustViewBounds=true; minimumHeight=200 }
        add(preview)
        add(TextView(this).apply { text="На Dimensity 7050 генерация может занимать много минут. Держи приложение открытым. Результаты сохраняются в галерею." })
        setContentView(ScrollView(this).apply { addView(layout) })
        refresh()
    }
    private fun refresh() {
        load.isEnabled=!working
        generate.isEnabled=!working && native.exists() && model.exists() && model.length()>1_000_000_000L
        if (!working) state.text=when {
            !native.exists() -> "Движок отсутствует в APK (ошибка сборки)"
            !model.exists() -> "Нужна однократная загрузка модели"
            else -> "Модель готова. Работает без интернета."
        }
        window.decorView.keepScreenOn=working
    }
    private fun show(s:String) { runOnUiThread { state.text=s } }
    private fun confirmDownload() {
        if (working) return
        AlertDialog.Builder(this).setTitle("Загрузка SD 1.5")
            .setMessage("Около 2,1 ГБ. Рекомендуется Wi-Fi. Оставь приложение открытым. После загрузки интернет не нужен.")
            .setPositiveButton("Скачать") { _,_-> download() }.setNegativeButton("Отмена",null).show()
    }
    private fun download() {
        if (working) return
        working=true; refresh()
        thread {
            val part=File(filesDir,"sd15.part")
            var connection: HttpURLConnection?=null
            try {
                var start=if(part.exists()) part.length() else 0L
                fun connect(offset:Long):HttpURLConnection =
                    (URL(link).openConnection() as HttpURLConnection).apply {
                        connectTimeout=20000; readTimeout=45000; instanceFollowRedirects=true
                        if(offset>0) setRequestProperty("Range","bytes=$offset-")
                    }
                connection=connect(start)
                if(start>0 && (connection!!.responseCode!=206 || !connection!!.getHeaderField("Content-Range").orEmpty().startsWith("bytes $start-"))) {
                    connection!!.disconnect(); part.delete(); start=0; connection=connect(0)
                }
                if(connection!!.responseCode !in 200..299) error("HTTP ${connection!!.responseCode}")
                val expected=connection!!.contentLengthLong.takeIf { it>0 }?.plus(start)
                connection!!.inputStream.use { src -> FileOutputStream(part,start>0).use { dst ->
                    val chunk=ByteArray(131072)
                    var count=start
                    var prev=-1
                    while(true) {
                        val n=src.read(chunk); if(n<0) break
                        dst.write(chunk,0,n); count+=n
                        val pct=if(expected!=null) (count*100/expected).toInt().coerceIn(0,100) else -1
                        if(pct!=prev) { prev=pct; runOnUiThread {
                            bar.progress=pct.coerceAtLeast(0)
                            state.text=if(pct>=0) "Загрузка: $pct%" else "Загружено ${count/1048576} МБ"
                        } }
                    }
                    dst.fd.sync()
                } }
                if(expected!=null && part.length()!=expected) error("Загрузка прервана")
                show("Проверка SHA-256…")
                val md=MessageDigest.getInstance("SHA-256")
                part.inputStream().use { input->
                    val chunk=ByteArray(1048576)
                    while(true) { val n=input.read(chunk); if(n<0) break; md.update(chunk,0,n) }
                }
                val actual=md.digest().joinToString("") { "%02x".format(it) }
                if(actual!=sha) { part.delete(); error("Неверная контрольная сумма; файл удалён") }
                if(model.exists()) model.delete()
                if(!part.renameTo(model)) error("Ошибка сохранения модели")
                show("Готово! Модель загружена.")
            } catch(ex:Exception) { show("Ошибка: ${ex.message}. Нажми скачать для продолжения.") }
            finally { connection?.disconnect(); working=false; runOnUiThread { refresh() } }
        }
    }
    private fun createImage() {
        if(working || !model.exists() || !native.exists()) return
        val text=prompt.text.toString().trim()
        if(text.isEmpty()) { state.text="Введите описание"; return }
        working=true; refresh()
        thread {
            val output=File(cacheDir,"generated-${System.currentTimeMillis()}.png")
            val log=File(cacheDir,"last-generation.log")
            try {
                show("Создаю изображение локально…")
                val args=listOf(native.absolutePath,"-m",model.absolutePath,"-p",text,
                    "-o",output.absolutePath,"-W","384","-H","384","--steps","20",
                    "--cfg-scale","7","--sampling-method","euler")
                val process=ProcessBuilder(args).directory(cacheDir).redirectErrorStream(true).redirectOutput(log).start()
                val exit=process.waitFor()
                if(exit!=0 || !output.exists() || output.length()<1024)
                    error("Код $exit: ${log.readText().takeLast(800)}")
                val uri=contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME,"Freedom_${System.currentTimeMillis()}.png")
                        put(MediaStore.Images.Media.MIME_TYPE,"image/png")
                        put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/FreedomDiffusion")
                        put(MediaStore.Images.Media.IS_PENDING,1)
                    }) ?: error("Не удалось открыть галерею")
                try {
                    contentResolver.openOutputStream(uri)?.use { dest ->
                        output.inputStream().use { it.copyTo(dest) }
                    } ?: error("Невозможно сохранить")
                    contentResolver.update(uri,ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING,0) },null,null)
                } catch(e:Exception) { contentResolver.delete(uri,null,null); throw e }
                runOnUiThread { preview.setImageURI(null); preview.setImageURI(uri); state.text="Готово! Изображение сохранено в галерею." }
            } catch(ex:Exception) { show("Ошибка генерации: ${ex.message}") }
            finally { working=false; runOnUiThread { refresh() } }
        }
    }
}