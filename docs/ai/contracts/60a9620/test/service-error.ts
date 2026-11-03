// Exact ServiceError excerpt from src/connected/store.ts at pinned 60a9620.
// Isolates schema/client verification from the bot's unrelated database dependencies.
export class ServiceError extends Error {
  constructor(readonly status: number, readonly code: string, message: string) { super(message); }
}
