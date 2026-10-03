import { NavLink, Route, Routes } from 'react-router'
import { cn } from '@/lib/utils'
import { ConnectorsPage } from './features/connectors/ConnectorsPage'
import { HealthStatus } from './features/health/HealthStatus'
import { RunDetailPage } from './features/runs/RunDetailPage'
import { WorkflowEditorPage } from './features/workflows/WorkflowEditorPage'
import { WorkflowsPage } from './features/workflows/WorkflowsPage'

function navClass({ isActive }: { isActive: boolean }) {
  return cn('text-sm hover:text-foreground', isActive ? 'font-medium text-foreground' : 'text-muted-foreground')
}

function App() {
  return (
    <div className="mx-auto max-w-5xl space-y-6 px-4 py-8">
      <header className="flex items-baseline justify-between gap-6">
        <div className="flex items-baseline gap-6">
          <h1 className="text-2xl font-semibold">Custom iPaaS</h1>
          <nav className="flex gap-4">
            <NavLink to="/" end className={navClass}>
              Workflows
            </NavLink>
            <NavLink to="/connectors" className={navClass}>
              Connectors
            </NavLink>
          </nav>
        </div>
        <div className="text-sm text-muted-foreground">
          <HealthStatus />
        </div>
      </header>
      <main>
        <Routes>
          <Route path="/" element={<WorkflowsPage />} />
          <Route path="/workflows/new" element={<WorkflowEditorPage />} />
          <Route path="/workflows/:id" element={<WorkflowEditorPage />} />
          <Route path="/runs/:id" element={<RunDetailPage />} />
          <Route path="/connectors" element={<ConnectorsPage />} />
        </Routes>
      </main>
    </div>
  )
}

export default App
