package com.teamfinder.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.yaml.snakeyaml.Yaml
import java.io.File

fun Application.configureSwagger() {
    val specFile = File("openapi.yaml")

    routing {
        // Swagger UI страница
        get("/swagger") {
            call.respondText(
                buildSwaggerHtml(),
                contentType = ContentType.Text.Html
            )
        }

        // OpenAPI spec в формате JSON
        get("/api/openapi.json") {
            try {
                if (!specFile.exists()) {
                    call.respond(HttpStatusCode.NotFound, "OpenAPI spec file not found")
                    return@get
                }

                val yaml = Yaml()
                val specMap = yaml.load<Map<String, Any>>(specFile.readText())

                call.respond(specMap)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Failed to parse OpenAPI spec: ${e.message}")
            }
        }

        // OpenAPI spec в формате YAML
        get("/api/openapi.yaml") {
            try {
                if (!specFile.exists()) {
                    call.respond(HttpStatusCode.NotFound, "OpenAPI spec file not found")
                    return@get
                }

                call.respondText(
                    specFile.readText(),
                    contentType = ContentType("application", "yaml")
                )
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Failed to read OpenAPI spec: ${e.message}")
            }
        }
    }
}

private fun buildSwaggerHtml(): String {
    return """
        <!DOCTYPE html>
        <html lang="ru">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>TeamFinder API — Swagger UI</title>
            <link rel="stylesheet" href="https://unpkg.com/swagger-ui-dist@5.11.0/swagger-ui.css">
            <style>
                html { box-sizing: border-box; overflow: -moz-scrollbars-vertical; overflow-y: scroll; }
                *, *:before, *:after { box-sizing: inherit; }
                body { margin: 0; background: #fafafa; }
                .topbar { display: none; }
                /* Тёмная тема для хедера */
                .swagger-ui .info { margin: 30px 0; }
                .swagger-ui .info .title { color: #1a1a2e; }
                .swagger-ui .info .description { color: #4a4a6a; }
            </style>
        </head>
        <body>
            <div id="swagger-ui"></div>
            <script src="https://unpkg.com/swagger-ui-dist@5.11.0/swagger-ui-bundle.js"></script>
            <script src="https://unpkg.com/swagger-ui-dist@5.11.0/swagger-ui-standalone-preset.js"></script>
            <script>
                window.onload = function() {
                    const ui = SwaggerUIBundle({
                        url: "/api/openapi.json",
                        dom_id: '#swagger-ui',
                        deepLinking: true,
                        presets: [
                            SwaggerUIBundle.presets.apis,
                            SwaggerUIStandalonePreset
                        ],
                        plugins: [
                            SwaggerUIBundle.plugins.DownloadUrl
                        ],
                        layout: "StandaloneLayout",
                        // Настройки для Bearer Auth
                        requestInterceptor: function(request) {
                            // Автоматически подставляем токен если есть
                            const token = localStorage.getItem('teamfinder_token');
                            if (token) {
                                request.headers.Authorization = 'Bearer ' + token;
                            }
                            return request;
                        },
                        onComplete: function() {
                            // Добавляем поле для ввода токена
                            const authWrapper = document.createElement('div');
                            authWrapper.style.cssText = 'position:fixed;top:0;left:0;right:0;background:#1a1a2e;color:#fff;padding:10px 20px;display:flex;align-items:center;gap:10px;z-index:9999;font-size:14px;';
                            authWrapper.innerHTML = `
                                <span style="font-weight:bold;">🔑 JWT Token:</span>
                                <input type="text" id="jwt-token-input" 
                                    style="flex:1;padding:6px 10px;border-radius:4px;border:1px solid #444;background:#2a2a4a;color:#fff;font-size:13px;" 
                                    placeholder="Вставьте access token для авторизации...">
                                <button onclick="saveToken()" 
                                    style="padding:6px 16px;border-radius:4px;border:none;background:#4caf50;color:#fff;cursor:pointer;font-size:13px;">
                                    Сохранить
                                </button>
                                <button onclick="clearToken()" 
                                    style="padding:6px 16px;border-radius:4px;border:none;background:#f44336;color:#fff;cursor:pointer;font-size:13px;">
                                    Очистить
                                </button>
                            `;
                            document.body.insertBefore(authWrapper, document.body.firstChild);
                            document.getElementById('swagger-ui').style.marginTop = '50px';
                            
                            // Восстанавливаем токен из localStorage
                            const savedToken = localStorage.getItem('teamfinder_token');
                            if (savedToken) {
                                document.getElementById('jwt-token-input').value = savedToken;
                            }
                        }
                    });
                    window.ui = ui;
                };

                function saveToken() {
                    const token = document.getElementById('jwt-token-input').value.trim();
                    if (token) {
                        localStorage.setItem('teamfinder_token', token);
                        alert('✅ Токен сохранён! Все запросы будут включать Authorization: Bearer');
                    }
                }

                function clearToken() {
                    localStorage.removeItem('teamfinder_token');
                    document.getElementById('jwt-token-input').value = '';
                    alert('🗑️ Токен удалён');
                }
            </script>
        </body>
        </html>
    """.trimIndent()
}
