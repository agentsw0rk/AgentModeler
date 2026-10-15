const API_BASE = process.env.NEXT_PUBLIC_API_URL || "";

export interface GeneratorInfo {
  id: string;
  displayName: string;
  supportedPlatforms: string[];
}

const FALLBACK_GENERATORS: GeneratorInfo[] = [{ id: "spring-boot", displayName: "Spring Boot (JVM)", supportedPlatforms: ["jvm", "native"] }];

export async function fetchGenerators(): Promise<GeneratorInfo[]> {
  try {
    const response = await fetch(`${API_BASE}/api/generators`);
    if (!response.ok) return FALLBACK_GENERATORS;
    return await response.json();
  } catch {
    // Backend not reachable (e.g. still starting): keep the dialog usable
    return FALLBACK_GENERATORS;
  }
}
