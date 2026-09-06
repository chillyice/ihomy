/**
 * 脑图模板库:新建脑图时可选择预设结构,避免每次从空白开始。
 * 数据格式与 simple-mind-map 节点树一致:{ data: { text, expand }, children: [] }。
 * 文案全部走 i18n(tools.mindmap.tpl.*),新增模板时在 zh-CN.js / en.js 同步补键。
 */

/** 简易节点构造 */
const n = (text, children = []) => ({ data: { text, expand: true }, children })

export const getMindmapTemplates = (t) => {
  const T = (k) => t(`tools.mindmap.tpl.${k}`)
  return [
    {
      key: 'project',
      layout: 'logicalStructure',
      tree: n(T('project.root'), [
        n(T('project.goal'), [n(T('project.goal1')), n(T('project.goal2'))]),
        n(T('project.milestone'), [n(T('project.milestone1')), n(T('project.milestone2'))]),
        n(T('project.tasks'), [n(T('project.tasks1')), n(T('project.tasks2'))]),
        n(T('project.risk'), [n(T('project.risk1')), n(T('project.risk2'))]),
      ]),
    },
    {
      key: 'weekly',
      layout: 'logicalStructure',
      tree: n(T('weekly.root'), [
        n(T('weekly.work'), [n(T('weekly.work1')), n(T('weekly.work2'))]),
        n(T('weekly.family'), [n(T('weekly.family1')), n(T('weekly.family2'))]),
        n(T('weekly.self'), [n(T('weekly.self1')), n(T('weekly.self2'))]),
      ]),
    },
    {
      key: 'reading',
      layout: 'logicalStructure',
      tree: n(T('reading.root'), [
        n(T('reading.info'), [n(T('reading.info1')), n(T('reading.info2'))]),
        n(T('reading.points'), [n(T('reading.points1')), n(T('reading.points2'))]),
        n(T('reading.quote'), [n(T('reading.quote1'))]),
        n(T('reading.action'), [n(T('reading.action1'))]),
      ]),
    },
    {
      key: 'meeting',
      layout: 'logicalStructure',
      tree: n(T('meeting.root'), [
        n(T('meeting.info'), [n(T('meeting.info1')), n(T('meeting.info2'))]),
        n(T('meeting.topic'), [n(T('meeting.topic1')), n(T('meeting.topic2'))]),
        n(T('meeting.conclusion'), [n(T('meeting.conclusion1'))]),
        n(T('meeting.todo'), [n(T('meeting.todo1'))]),
      ]),
    },
    {
      key: 'problem',
      layout: 'fishbone',
      tree: n(T('problem.root'), [
        n(T('problem.symptom'), [n(T('problem.symptom1')), n(T('problem.symptom2'))]),
        n(T('problem.cause'), [n(T('problem.cause1')), n(T('problem.cause2'))]),
        n(T('problem.solution'), [n(T('problem.solution1'))]),
        n(T('problem.verify'), [n(T('problem.verify1'))]),
      ]),
    },
    {
      key: 'trip',
      layout: 'logicalStructure',
      tree: n(T('trip.root'), [
        n(T('trip.dest'), [n(T('trip.dest1')), n(T('trip.dest2'))]),
        n(T('trip.schedule'), [n(T('trip.schedule1')), n(T('trip.schedule2'))]),
        n(T('trip.budget'), [n(T('trip.budget1')), n(T('trip.budget2'))]),
        n(T('trip.equipment'), [n(T('trip.equipment1')), n(T('trip.equipment2'))]),
      ]),
    },
  ]
}
