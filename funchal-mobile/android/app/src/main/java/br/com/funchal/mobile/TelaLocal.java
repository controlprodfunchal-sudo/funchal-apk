package br.com.funchal.mobile;

import android.content.Context;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A tela baixada (index.html novo) NUNCA é aberta como file:// -- o Android
 * nega esse acesso (net::ERR_ACCESS_DENIED) e o app ficava em branco.
 * Em vez disso, a tela baixada é entregue pelo mesmo endereço interno do app
 * (https://localhost/), no lugar da de fábrica. Assim:
 *   - o endereço (origem) é sempre o mesmo, e os dados salvos no aparelho
 *     continuam ali depois de cada atualização;
 *   - se a tela baixada estiver ausente, incompleta ou estragada, ela é
 *     ignorada e o app abre a de fábrica, que vem dentro do .apk e funciona
 *     sem internet.
 */
final class TelaLocal {
    static final String URL_BASE = "https://localhost/";
    private static final Pattern VERSAO = Pattern.compile("APP_VERSION\\s*=\\s*'([^']+)'");

    private TelaLocal() {}

    static File pasta(Context c) { return new File(c.getFilesDir(), "tela"); }
    static File ativa(Context c) { return new File(pasta(c), "index.html"); }
    static File baixando(Context c) { return new File(pasta(c), "index.baixando.html"); }

    /** Lê o APP_VERSION de dentro de um HTML; null se não for uma tela válida. */
    static String versaoDe(InputStream in) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
            String linha, versao = null;
            boolean fechou = false;
            while ((linha = r.readLine()) != null) {
                if (versao == null) {
                    Matcher m = VERSAO.matcher(linha);
                    if (m.find()) versao = m.group(1);
                }
                if (linha.contains("</html>")) fechou = true;
            }
            return fechou ? versao : null;
        } catch (Exception e) { return null; }
    }

    /** A tela baixada só vale se estiver inteira: tamanho razoável, versão e fechamento do HTML. */
    static String versaoValida(File f) {
        try {
            if (f == null || !f.isFile() || f.length() < 50_000) return null;
            try (InputStream in = new FileInputStream(f)) { return versaoDe(in); }
        } catch (Exception e) { return null; }
    }

    static String versaoDeFabrica(Context c) {
        try (InputStream in = c.getAssets().open("public/index.html")) { return versaoDe(in); }
        catch (Exception e) { return null; }
    }

    /** Chamado na abertura do app: descarta a tela baixada se estiver estragada ou for mais velha que a de fábrica. */
    static void conferir(Context c) {
        File f = ativa(c);
        if (!f.exists()) return;
        String baixada = versaoValida(f);
        String fabrica = versaoDeFabrica(c);
        if (baixada == null || (fabrica != null && fabrica.compareTo(baixada) >= 0)) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
            c.getSharedPreferences("funchal_app", Context.MODE_PRIVATE).edit()
                .remove("tela_versao").remove("tela_versao_baixada").putInt("tela_tentativas", 0).apply();
        }
    }

    /** Responde a abertura da tela principal com a tela baixada, se houver uma boa. */
    static WebResourceResponse tentar(Context c, WebResourceRequest req) {
        try {
            if (req == null || !"GET".equalsIgnoreCase(req.getMethod())) return null;
            Uri u = req.getUrl();
            if (u == null || !"localhost".equals(u.getHost())) return null;
            String p = u.getPath();
            if (!(p == null || p.isEmpty() || p.equals("/") || p.equals("/index.html"))) return null;
            File f = ativa(c);
            if (!f.isFile()) return null;
            WebResourceResponse r = new WebResourceResponse("text/html", "UTF-8", new FileInputStream(f));
            Map<String, String> h = new HashMap<>();
            h.put("Cache-Control", "no-store");
            r.setResponseHeaders(h);
            return r;
        } catch (Throwable e) { return null; }
    }
}
