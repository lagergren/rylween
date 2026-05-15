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
              <input name="name" autocomplete="username" value="${displayName.htmlEscaped()}" required>
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
                  <input name="email" type="email" autocomplete="email" value="${email.htmlEscaped()}" required>
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
