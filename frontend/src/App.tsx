import { ConnectorsPage } from './features/connectors/ConnectorsPage'
import { HealthStatus } from './features/health/HealthStatus'

function App() {
  return (
    <div className="mx-auto max-w-5xl space-y-6 px-4 py-8">
      <header className="flex items-baseline justify-between">
        <h1 className="text-2xl font-semibold">Custom iPaaS</h1>
        <div className="text-sm text-muted-foreground">
          <HealthStatus />
        </div>
      </header>
      <main>
        <ConnectorsPage />
      </main>
    </div>
  )
}

export default App
