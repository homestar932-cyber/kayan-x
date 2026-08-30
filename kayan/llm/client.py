import requests
from typing import Any, Dict, List


class LLMError(RuntimeError):
    pass


class LLMClient:
    """Minimal llama-server OpenAI-compatible client."""

    def __init__(self, config):
        self.base_url = config.server_url.rstrip("/")
        self.timeout = config.request_timeout

    def chat(self, messages: List[Dict[str, str]], temperature: float = 0.1) -> str:
        url = f"{self.base_url}/v1/chat/completions"
        payload = {
            "messages": messages,
            "temperature": temperature,
            "stream": False,
        }
        try:
            r = requests.post(url, json=payload, timeout=self.timeout)
            r.raise_for_status()
            data = r.json()
            choices = data.get("choices") or []
            if not choices:
                raise LLMError(f"llama-server returned no choices: {data}")
            return choices[0]["message"]["content"]
        except requests.RequestException as e:
            raise LLMError(f"LLM connection failed: {e}") from e
        except (KeyError, TypeError, ValueError) as e:
            raise LLMError(f"Invalid LLM response: {e}") from e
