use std::collections::HashSet;

#[derive(Clone, Debug, PartialEq)]
pub enum JsonValue {
    Null,
    Boolean(bool),
    Number(String),
    String(String),
    Array(Vec<JsonValue>),
    Object(Vec<(String, JsonValue)>),
}

pub fn parse(source: &str) -> Result<JsonValue, String> {
    JsonParser::new(source).parse()
}

pub fn required<'a>(
    object: &'a [(String, JsonValue)],
    name: &str,
) -> Result<&'a JsonValue, String> {
    object
        .iter()
        .find(|(key, _)| key == name)
        .map(|(_, value)| value)
        .ok_or_else(|| format!("missing JSON field {name}"))
}

pub fn required_array<'a>(
    object: &'a [(String, JsonValue)],
    name: &str,
) -> Result<&'a [JsonValue], String> {
    required(object, name)?.as_array(name)
}

pub fn required_object<'a>(
    object: &'a [(String, JsonValue)],
    name: &str,
) -> Result<&'a [(String, JsonValue)], String> {
    required(object, name)?.as_object(name)
}

pub fn required_string<'a>(
    object: &'a [(String, JsonValue)],
    name: &str,
) -> Result<&'a str, String> {
    required(object, name)?.as_string(name).map(String::as_str)
}

impl JsonValue {
    pub fn as_object(&self, label: &str) -> Result<&[(String, JsonValue)], String> {
        match self {
            JsonValue::Object(entries) => Ok(entries),
            _ => Err(format!("expected object for {label}")),
        }
    }

    pub fn as_array(&self, label: &str) -> Result<&[JsonValue], String> {
        match self {
            JsonValue::Array(values) => Ok(values),
            _ => Err(format!("expected array for {label}")),
        }
    }

    pub fn as_string(&self, label: &str) -> Result<&String, String> {
        match self {
            JsonValue::String(value) => Ok(value),
            _ => Err(format!("expected string for {label}")),
        }
    }

    pub fn as_number(&self, label: &str) -> Result<&str, String> {
        match self {
            JsonValue::Number(value) => Ok(value),
            _ => Err(format!("expected number for {label}")),
        }
    }

    pub fn row_count(&self) -> usize {
        match self {
            JsonValue::Array(values) => values.len(),
            _ => 1,
        }
    }

    pub fn render_compact(&self) -> String {
        let mut output = String::new();
        self.append_json(&mut output);
        output
    }

    fn append_json(&self, output: &mut String) {
        match self {
            JsonValue::Null => output.push_str("null"),
            JsonValue::Boolean(value) => output.push_str(if *value { "true" } else { "false" }),
            JsonValue::Number(value) => output.push_str(value),
            JsonValue::String(value) => append_quoted_json(output, value),
            JsonValue::Array(values) => append_array(output, values),
            JsonValue::Object(entries) => append_object(output, entries),
        }
    }
}

fn append_array(output: &mut String, values: &[JsonValue]) {
    output.push('[');
    for (index, value) in values.iter().enumerate() {
        if index > 0 {
            output.push(',');
        }
        value.append_json(output);
    }
    output.push(']');
}

fn append_object(output: &mut String, entries: &[(String, JsonValue)]) {
    output.push('{');
    for (index, (key, value)) in entries.iter().enumerate() {
        if index > 0 {
            output.push(',');
        }
        append_quoted_json(output, key);
        output.push(':');
        value.append_json(output);
    }
    output.push('}');
}

fn append_quoted_json(output: &mut String, value: &str) {
    output.push('"');
    for ch in value.chars() {
        match ch {
            '\\' => output.push_str("\\\\"),
            '"' => output.push_str("\\\""),
            '\u{0008}' => output.push_str("\\b"),
            '\u{000c}' => output.push_str("\\f"),
            '\n' => output.push_str("\\n"),
            '\r' => output.push_str("\\r"),
            '\t' => output.push_str("\\t"),
            ch if ch < ' ' => output.push_str(&format!("\\u{:04x}", ch as u32)),
            _ => output.push(ch),
        }
    }
    output.push('"');
}

struct JsonParser {
    source: Vec<char>,
    pos: usize,
}

impl JsonParser {
    fn new(source: &str) -> Self {
        Self {
            source: source.chars().collect(),
            pos: 0,
        }
    }

    fn parse(mut self) -> Result<JsonValue, String> {
        let value = self.value()?;
        self.skip_whitespace();
        if self.pos != self.source.len() {
            return self.fail("trailing characters");
        }
        Ok(value)
    }

    fn value(&mut self) -> Result<JsonValue, String> {
        self.skip_whitespace();
        let Some(ch) = self.current() else {
            return self.fail("unexpected end of file");
        };
        match ch {
            '{' => self.object(),
            '[' => self.array(),
            '"' => self.string().map(JsonValue::String),
            't' => self.literal("true", JsonValue::Boolean(true)),
            'f' => self.literal("false", JsonValue::Boolean(false)),
            'n' => self.literal("null", JsonValue::Null),
            '-' | '0'..='9' => self.number().map(JsonValue::Number),
            other => self.fail(&format!("unexpected character {other}")),
        }
    }

    fn object(&mut self) -> Result<JsonValue, String> {
        self.expect('{')?;
        self.skip_whitespace();
        let mut entries = Vec::new();
        let mut keys = HashSet::new();
        if self.peek('}') {
            self.expect('}')?;
            return Ok(JsonValue::Object(entries));
        }
        loop {
            let key = self.string()?;
            if !keys.insert(key.clone()) {
                return self.fail("duplicate object key");
            }
            self.skip_whitespace();
            self.expect(':')?;
            entries.push((key, self.value()?));
            self.skip_whitespace();
            if self.peek(',') {
                self.expect(',')?;
                self.skip_whitespace();
            } else if self.peek('}') {
                self.expect('}')?;
                break;
            } else {
                return self.fail("expected ',' or '}'");
            }
        }
        Ok(JsonValue::Object(entries))
    }

    fn array(&mut self) -> Result<JsonValue, String> {
        self.expect('[')?;
        self.skip_whitespace();
        let mut values = Vec::new();
        if self.peek(']') {
            self.expect(']')?;
            return Ok(JsonValue::Array(values));
        }
        loop {
            values.push(self.value()?);
            self.skip_whitespace();
            if self.peek(',') {
                self.expect(',')?;
            } else if self.peek(']') {
                self.expect(']')?;
                break;
            } else {
                return self.fail("expected ',' or ']'");
            }
        }
        Ok(JsonValue::Array(values))
    }

    fn string(&mut self) -> Result<String, String> {
        self.skip_whitespace();
        self.expect('"')?;
        let mut value = String::new();
        while self.pos < self.source.len() {
            let ch = self.source[self.pos];
            self.pos += 1;
            match ch {
                '"' => return Ok(value),
                '\\' => value.push(self.escape()?),
                ch if ch < ' ' => return self.fail("unescaped control character"),
                _ => value.push(ch),
            }
        }
        self.fail("unterminated string")
    }

    fn escape(&mut self) -> Result<char, String> {
        let Some(ch) = self.current() else {
            return self.fail("unterminated escape");
        };
        self.pos += 1;
        match ch {
            '"' | '\\' | '/' => Ok(ch),
            'b' => Ok('\u{0008}'),
            'f' => Ok('\u{000c}'),
            'n' => Ok('\n'),
            'r' => Ok('\r'),
            't' => Ok('\t'),
            'u' => self.unicode_escape(),
            _ => self.fail("bad escape"),
        }
    }

    fn unicode_escape(&mut self) -> Result<char, String> {
        if self.pos + 4 > self.source.len() {
            return self.fail("bad unicode escape");
        }
        let code = self.source[self.pos..self.pos + 4]
            .iter()
            .collect::<String>();
        self.pos += 4;
        let value = u32::from_str_radix(&code, 16).map_err(|_| self.error("bad unicode escape"))?;
        char::from_u32(value).ok_or_else(|| self.error("bad unicode escape"))
    }

    fn number(&mut self) -> Result<String, String> {
        let start = self.pos;
        if self.peek('-') {
            self.pos += 1;
        }
        self.digits()?;
        if self.peek('.') {
            self.pos += 1;
            self.digits()?;
        }
        if self.peek('e') || self.peek('E') {
            self.pos += 1;
            if self.peek('+') || self.peek('-') {
                self.pos += 1;
            }
            self.digits()?;
        }
        Ok(self.source[start..self.pos].iter().collect())
    }

    fn digits(&mut self) -> Result<(), String> {
        let start = self.pos;
        while self.current().is_some_and(|ch| ch.is_ascii_digit()) {
            self.pos += 1;
        }
        if self.pos == start {
            self.fail("expected digit")
        } else {
            Ok(())
        }
    }

    fn literal(&mut self, text: &str, value: JsonValue) -> Result<JsonValue, String> {
        for expected in text.chars() {
            self.expect(expected)?;
        }
        Ok(value)
    }

    fn skip_whitespace(&mut self) {
        while self.current().is_some_and(char::is_whitespace) {
            self.pos += 1;
        }
    }

    fn expect(&mut self, ch: char) -> Result<(), String> {
        if self.peek(ch) {
            self.pos += 1;
            Ok(())
        } else {
            self.fail(&format!("expected '{ch}'"))
        }
    }

    fn peek(&self, ch: char) -> bool {
        self.current() == Some(ch)
    }

    fn current(&self) -> Option<char> {
        self.source.get(self.pos).copied()
    }

    fn fail<T>(&self, message: &str) -> Result<T, String> {
        Err(self.error(message))
    }

    fn error(&self, message: &str) -> String {
        format!("JSON parse failed at char {}: {message}", self.pos)
    }
}
