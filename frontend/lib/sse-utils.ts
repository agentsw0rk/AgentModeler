export function parseSSEStream<T>(
  body: ReadableStream<Uint8Array>,
  onEvent: (event: T) => void
) {
  const reader = body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  function processChunk() {
    reader.read().then(({ done, value }) => {
      if (done) return;
      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split("\n");
      buffer = lines.pop() || "";
      let currentData = "";
      for (const line of lines) {
        if (line.startsWith("data:")) {
          currentData = line.slice(5).trim();
        } else if (line === "" && currentData) {
          try {
            const parsed = JSON.parse(currentData) as T;
            onEvent(parsed);
          } catch { /* skip */ }
          currentData = "";
        }
      }
      processChunk();
    });
  }
  processChunk();
}
