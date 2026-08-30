import json
import re


def extract_json(text: str):
    """Extract the first valid JSON object from a model response."""
    text = text.strip()
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        pass

    fenced = re.search(r"```(?:json)?\s*(.*?)\s*```", text, re.S | re.I)
    candidates = [fenced.group(1)] if fenced else []
    candidates.append(text)

    for candidate in candidates:
        try:
            return json.loads(candidate.strip())
        except json.JSONDecodeError:
            continue

    # Scan balanced object boundaries.
    start = text.find("{")
    while start >= 0:
        depth = 0
        in_string = False
        escape = False
        for i in range(start, len(text)):
            ch = text[i]
            if in_string:
                if escape:
                    escape = False
                elif ch == "\\":
                    escape = True
                elif ch == '"':
                    in_string = False
                continue
            if ch == '"':
                in_string = True
            elif ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    try:
                        return json.loads(text[start:i+1])
                    except json.JSONDecodeError:
                        break
        start = text.find("{", start + 1)

    raise ValueError("No valid JSON object found in model response")
