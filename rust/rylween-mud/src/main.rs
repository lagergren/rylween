mod bundle;
mod json;
mod sha256;

use std::env;
use std::path::PathBuf;
use std::process;

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
        Command::Inspect { bundle } => bundle::inspect_bundle(bundle),
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
            "--listen" => return Err("--listen is reserved for the K1 TCP server slice".to_owned()),
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

fn print_help() {
    println!("Usage: rylween-mud --bundle <path> inspect");
    println!();
    println!(
        "Experimental K1 Rust target. The current slice verifies the J1 compiled bundle envelope and section hash."
    );
}
