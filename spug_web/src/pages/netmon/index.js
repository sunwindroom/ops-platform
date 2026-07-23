/**
 * Copyright (c) OpenSpug Organization. https://github.com/openspug/spug
 * Copyright (c) <spug.dev@gmail.com>
 * Released under the AGPL-3.0 License.
 */
import React, { useEffect } from 'react';
import { Tabs } from 'antd';
import { AuthDiv, Breadcrumb } from 'components';
import Overview from './Overview';
import Topology from './Topology';
import Devices from './Devices';
import Anomaly from './Anomaly';
import Discovery from './Discovery';
import Reports from './Reports';
import DeviceDetail from './DeviceDetail';
import store from './store';

export default function NetmonIndex() {
  useEffect(() => {
    store.autoReload = true;
    store.fetchGroups();
    return () => { store.autoReload = false; store.stopDiscoveryPolling() }
  }, []);

  return (
    <AuthDiv auth="netmon.device.view">
      <Breadcrumb>
        <Breadcrumb.Item>首页</Breadcrumb.Item>
        <Breadcrumb.Item>IT资源监控</Breadcrumb.Item>
      </Breadcrumb>
      <Tabs
        defaultActiveKey="overview"
        type="card"
        items={[
          { key: 'overview', label: '实时总览', children: <Overview/> },
          { key: 'topology', label: '拓扑视图', children: <Topology/> },
          { key: 'devices', label: '资源台账', children: <Devices/> },
          { key: 'anomaly', label: '异常事件', children: <Anomaly/> },
          { key: 'discovery', label: '自动发现', children: <Discovery/> },
          { key: 'reports', label: '报表管理', children: <Reports/> },
        ]}
      />
      <DeviceDetail/>
    </AuthDiv>
  )
}
