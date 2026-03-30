const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

export interface GeneratorInfo {
  id: string;
  displayName: string;
  supportedPlatforms: string[];
}

export async function fetchGenerators(): Promise<GeneratorInfo[]> {
  const response = await fetch(`${API_BASE}/api/generators`);
  if (!response.ok) return [{ id: "spring-boot", displayName: "Spring Boot (JVM)", supportedPlatforms: ["jvm", "native"] }];
  return response.json();
}
