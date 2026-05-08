use std::env;
use std::fs;
use std::path::PathBuf;
use std::process;

const EXPECTED_FORMAT: &str = "rylween.compiled.bundle";
const EXPECTED_SCHEMA_VERSION: &str = "3";

fn main() {
    if let Err(error) = run() {
        eprintln!("rylween-mud: {error}");
        process::exit(1);
    }
}

fn run() -> Result<(), String> {
    match parse_args(env::args().skip(1))? {
        Command::Help => {
            print_help();
            Ok(())
        }
        Command::Inspect { bundle } => inspect_bundle(bundle),
    }
}

enum Command {
    Help,
    Inspect { bundle: PathBuf },
}

fn parse_args(args: impl Iterator<Item = String>) -> Result<Command, String> {
    let mut bundle = None;
    let mut inspect = false;
    let mut values = args.peekable();

    while let Some(arg) = values.next() {
        match arg.as_str() {
            "--help" | "-h" => return Ok(Command::Help),
            "--bundle" => {
                let value = values.next().ok_or("--bundle requires a path")?;
                bundle = Some(PathBuf::from(value));
            }
            "inspect" => inspect = true,
            "--listen" => {
                return Err("--listen is reserved for the K1 TCP server slice".to_owned());
            }
            other => return Err(format!("unexpected argument '{other}'")),
        }
    }

    if inspect {
        let bundle = bundle.ok_or("inspect requires --bundle <path>")?;
        Ok(Command::Inspect { bundle })
    } else {
        Err("expected inspect command".to_owned())
    }
}

fn inspect_bundle(bundle: PathBuf) -> Result<(), String> {
    let source = fs::read_to_string(&bundle)
        .map_err(|error| format!("failed to read {}: {error}", bundle.display()))?;
    let format = string_field(&source, "format").ok_or("missing top-level format")?;
    let schema_version =
        number_field(&source, "schemaVersion").ok_or("missing top-level schemaVersion")?;
    let content_hash =
        string_field(&source, "bundleContentHash").ok_or("missing top-level bundleContentHash")?;

    if format != EXPECTED_FORMAT {
        return Err(format!("unsupported bundle format '{format}'"));
    }
    if schema_version != EXPECTED_SCHEMA_VERSION {
        return Err(format!("unsupported bundle schemaVersion {schema_version}"));
    }
    if !is_sha256_hash(&content_hash) {
        return Err("bundleContentHash must be sha256:<64 lowercase hex chars>".to_owned());
    }

    println!("rylween-mud inspect");
    println!("bundle={}", bundle.display());
    println!("bytes={}", source.len());
    println!("format={format}");
    println!("schemaVersion={schema_version}");
    println!("bundleContentHash={content_hash}");
    println!("status=experimental-k1.0-skeleton");
    Ok(())
}

fn string_field(source: &str, key: &str) -> Option<String> {
    let rest = after_field_colon(source, key)?.trim_start();
    let quoted = rest.strip_prefix('"')?;
    let mut result = String::new();
    let mut escaped = false;
    for ch in quoted.chars() {
        if escaped {
            result.push(match ch {
                '"' => '"',
                '\\' => '\\',
                '/' => '/',
                'b' => '\u{0008}',
                'f' => '\u{000c}',
                'n' => '\n',
                'r' => '\r',
                't' => '\t',
                other => other,
            });
            escaped = false;
        } else if ch == '\\' {
            escaped = true;
        } else if ch == '"' {
            return Some(result);
        } else {
            result.push(ch);
        }
    }
    None
}

fn number_field(source: &str, key: &str) -> Option<String> {
    let rest = after_field_colon(source, key)?.trim_start();
    let value: String = rest.chars().take_while(|ch| ch.is_ascii_digit()).collect();
    if value.is_empty() { None } else { Some(value) }
}

fn after_field_colon<'a>(source: &'a str, key: &str) -> Option<&'a str> {
    let needle = format!("\"{key}\"");
    let after_key = source.find(&needle)? + needle.len();
    let rest = &source[after_key..];
    let colon = rest.find(':')?;
    Some(&rest[colon + 1..])
}

fn is_sha256_hash(value: &str) -> bool {
    let Some(hash) = value.strip_prefix("sha256:") else {
        return false;
    };
    hash.len() == 64
        && hash
            .chars()
            .all(|ch| ch.is_ascii_hexdigit() && !ch.is_ascii_uppercase())
}

fn print_help() {
    println!("Usage: rylween-mud --bundle <path> inspect");
    println!();
    println!(
        "Experimental K1 Rust target. The current slice only inspects the J1 compiled bundle envelope."
    );
}
