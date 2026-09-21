import { OverviewSection } from '../components/dashboard/OverviewSection'
import { ActiveCourseList } from '../components/dashboard/ActiveCourseList'
import { AttentionPanel } from '../components/dashboard/AttentionPanel'
import { RecentCompletionList } from '../components/dashboard/RecentCompletionList'
import { RecentActivityTable } from '../components/dashboard/RecentActivityTable'

export function DashboardPage() {
  return (
    <div className="dashboard">
      <OverviewSection />
      <div className="dashboard-columns"><ActiveCourseList /><div className="dashboard-aside"><AttentionPanel /><RecentCompletionList /></div></div>
      <RecentActivityTable />
    </div>
  )
}
