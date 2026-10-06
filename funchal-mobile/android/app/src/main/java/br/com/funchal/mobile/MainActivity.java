package br.com.funchal.mobile;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import com.getcapacitor.BridgeActivity;
import com.getcapacitor.BridgeWebViewClient;

import java.io.File;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WebView webView = this.bridge.getWebView();
        webView.addJavascriptInterface(new AndroidTeste(this, webView), "AndroidTeste");

        SharedPreferences prefs = getSharedPreferences("funchal_app", MODE_PRIVATE);

        /* 1) Descarta a tela baixada se estiver estragada ou mais velha que a de fábrica. */
        TelaLocal.conferir(this);

        /* 2) Trava de segurança: se a tela baixada abriu 3 vezes sem nunca se confirmar
              (AndroidTeste.telaOk), ela é descartada e o app volta para a de fábrica. */
        File baixada = TelaLocal.ativa(this);
        if (baixada.exists()) {
            int tentativas = prefs.getInt("tela_tentativas", 0) + 1;
            if (tentativas > 3) {
                //noinspection ResultOfMethodCallIgnored
                baixada.delete();
                prefs.edit().remove("tela_versao").remove("tela_versao_baixada").putInt("tela_tentativas", 0).apply();
            } else {
                prefs.edit().putInt("tela_tentativas", tentativas).apply();
            }
        }

        /* 3) A tela baixada (se boa) é entregue pelo endereço interno do app -- nunca por file://. */
        this.bridge.setWebViewClient(new BridgeWebViewClient(this.bridge) {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                WebResourceResponse r = TelaLocal.tentar(MainActivity.this, request);
                return r != null ? r : super.shouldInterceptRequest(view, request);
            }
        });

        if (TelaLocal.ativa(this).exists()) {
            webView.loadUrl(TelaLocal.URL_BASE);
        }

        /* Sem tela baixada: registra a versão que veio de fábrica, para a comparação funcionar. */
        if (!TelaLocal.ativa(this).exists() && prefs.getString("tela_versao", null) == null) {
            String versao = TelaLocal.versaoDeFabrica(this);
            if (versao != null) prefs.edit().putString("tela_versao", versao).apply();
        }
    }
}
