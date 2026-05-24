/** Server-rendered login and registration page for browser runtime accounts. */

package rylween.webcommon.auth

enum class WebAuthPageMode { Login, Register }

fun webAuthPageHtml(
    mode: WebAuthPageMode,
    error: String? = null,
    email: String = "",
    displayName: String = "",
    backgroundImage: String? = null,
): String {
    val registering = mode == WebAuthPageMode.Register
    val title = if (registering) "Register" else "Log In"
    val action = if (registering) "/api/register" else "/api/login"
    val alternateHref = if (registering) "/login" else "/register"
    val alternateText = if (registering) "Already have an account?" else "Need an account?"
    val alternateAction = if (registering) "Log in" else "Register"
    val nameField =
        if (registering) {
            """
            <label>
              Adventurer name
              <input id="name" name="name" autocomplete="username" value="${displayName.htmlEscaped()}" required>
              <span class="field-status" data-field-status="name" aria-live="polite"></span>
            </label>
            """.trimIndent()
        } else {
            ""
        }
    val errorBlock = error?.let { """<div class="error">${it.htmlEscaped()}</div>""" }.orEmpty()
    val bodyStyle = backgroundImage?.let { """ style="--auth-bg: url('${it.htmlEscaped()}')"""" }.orEmpty()
    return """
        <!doctype html>
        <html lang="en">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>Rylween $title</title>
          <link rel="stylesheet" href="/auth.css">
        </head>
        <body$bodyStyle>
          <main class="auth-shell">
            <section class="panel">
              <p class="eyebrow">Adventurers of Rylween</p>
              <h1>$title</h1>
              $errorBlock
              <form method="post" action="$action">
                $nameField
                <label>
                  Email
                  <input id="email" name="email" type="email" autocomplete="email" value="${email.htmlEscaped()}" required>
                  ${if (registering) """<span class="field-status" data-field-status="email" aria-live="polite"></span>""" else ""}
                </label>
                <label>
                  Password
                  <input name="password" type="password" autocomplete="${if (registering) "new-password" else "current-password"}" required>
                </label>
                <button type="submit">$title</button>
              </form>
              <p class="switch">$alternateText <a href="$alternateHref">$alternateAction</a></p>
            </section>
          </main>
          ${if (registering) registrationAvailabilityScript() else ""}
        </body>
        </html>
        """.trimIndent()
}

private fun String.htmlEscaped(): String =
    buildString(length) {
        this@htmlEscaped.forEach { char ->
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(char)
            }
        }
    }

private fun registrationAvailabilityScript(): String =
    """
    <script>
    (() => {
      const form = document.querySelector("form");
      const name = document.querySelector("input[name='name']");
      const email = document.querySelector("input[name='email']");
      const submit = document.querySelector("button[type='submit']");
      const status = {
        name: document.querySelector("[data-field-status='name']"),
        email: document.querySelector("[data-field-status='email']")
      };
      let latest = 0;
      let availability = { name: false, email: false };

      function setStatus(field, available, message) {
        availability[field] = Boolean(available);
        const element = status[field];
        if (!element) return;
        element.textContent = message ? (available ? "✓ " : "✕ ") + message : "";
        element.className = "field-status " + (message ? (available ? "ok" : "bad") : "");
      }

      function syncSubmit() {
        if (!submit) return;
        submit.disabled = Boolean(name?.value && email?.value) && !(availability.name && availability.email);
      }

      async function check() {
        const id = ++latest;
        const query = new URLSearchParams({ name: name?.value || "", email: email?.value || "" });
        if (!query.get("name") && !query.get("email")) {
          setStatus("name", false, "");
          setStatus("email", false, "");
          syncSubmit();
          return;
        }
        try {
          const response = await fetch("/api/auth/availability?" + query.toString(), { headers: { "Accept": "application/json" } });
          if (id !== latest || !response.ok) return;
          const body = await response.json();
          setStatus("name", body.name?.available, body.name?.message || "");
          setStatus("email", body.email?.available, body.email?.message || "");
        } catch (_error) {
          setStatus("name", false, "");
          setStatus("email", false, "");
        }
        syncSubmit();
      }

      let timer = 0;
      function schedule() {
        window.clearTimeout(timer);
        timer = window.setTimeout(check, 180);
      }

      name?.addEventListener("input", schedule);
      email?.addEventListener("input", schedule);
      form?.addEventListener("submit", (event) => {
        if (!(availability.name && availability.email)) {
          event.preventDefault();
          check();
        }
      });
      check();
    })();
    </script>
    """.trimIndent()
