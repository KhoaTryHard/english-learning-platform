"""The W9 runbook must refuse shared targets before any DB/restart command."""
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from test_runtime import fresh_report, require_docker_test


class TestRuntimePreflight(unittest.TestCase):
    def setUp(self):
        self.env = patch.dict(os.environ, {'COMPOSE_PROJECT_NAME': 'khoa-backlog-regression',
            'ALLOW_TEST_FAULT_INJECTION': '1', 'GATEWAY_URL': 'http://127.0.0.1:18080'}, clear=True)
        self.env.start()
        self.addCleanup(self.env.stop)

    def inspect(self, command, **kwargs):
        service = command[-1]
        if command[1] == 'compose':
            return service
        ports = {'8080/tcp': [{'HostIp': '127.0.0.1', 'HostPort': '18080'}]} if service == 'api' else {}
        return json.dumps([{'Config': {'Labels': {'com.docker.compose.project': 'khoa-backlog-regression',
            'com.docker.compose.service': service}}, 'NetworkSettings': {'Ports': ports}}])

    @patch('test_runtime.subprocess.check_output')
    def test_shared_project_rejected_before_docker(self, run):
        os.environ['COMPOSE_PROJECT_NAME'] = 'production'
        with self.assertRaises(ValueError):
            require_docker_test(['user-db'])
        run.assert_not_called()

    @patch('test_runtime.subprocess.check_output')
    def test_missing_opt_in_rejected_before_docker(self, run):
        del os.environ['ALLOW_TEST_FAULT_INJECTION']
        with self.assertRaises(ValueError):
            require_docker_test(['user-db'])
        run.assert_not_called()

    @patch('test_runtime.subprocess.check_output')
    def test_remote_gateway_rejected_before_docker(self, run):
        os.environ['GATEWAY_URL'] = 'https://production.example'
        with self.assertRaises(ValueError):
            require_docker_test(['user-db'])
        run.assert_not_called()

    def test_selected_loopback_stack_accepted(self):
        with patch('test_runtime.subprocess.check_output', side_effect=self.inspect):
            self.assertEqual('khoa-backlog-regression', require_docker_test(['user', 'user-db']))

    def test_container_label_mismatch_rejected(self):
        def changed(command, **kwargs):
            return self.inspect(command, **kwargs).replace('khoa-backlog-regression', 'another-project')
        with patch('test_runtime.subprocess.check_output', side_effect=changed), self.assertRaises(ValueError):
            require_docker_test(['user-db'])

    def test_gateway_port_mismatch_rejected(self):
        os.environ['GATEWAY_URL'] = 'http://localhost:8080'
        with patch('test_runtime.subprocess.check_output', side_effect=self.inspect), self.assertRaises(ValueError):
            require_docker_test(['user-db'])

    def test_public_port_rejected(self):
        def changed(command, **kwargs):
            return self.inspect(command, **kwargs).replace('127.0.0.1', '0.0.0.0')
        with patch('test_runtime.subprocess.check_output', side_effect=changed), self.assertRaises(ValueError):
            require_docker_test(['user-db'])

    def test_published_db_rejected(self):
        def changed(command, **kwargs):
            raw = self.inspect(command, **kwargs)
            if command[1] == 'inspect' and command[-1] == 'user-db':
                value = json.loads(raw)
                value[0]['NetworkSettings']['Ports'] = {'5432/tcp': [{'HostIp': '127.0.0.1', 'HostPort': '15432'}]}
                return json.dumps(value)
            return raw
        with patch('test_runtime.subprocess.check_output', side_effect=changed), self.assertRaises(ValueError):
            require_docker_test(['user-db'])

    def test_old_report_rejected_without_rewriting(self):
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / 'report.json'
            report.write_text('original evidence')
            with self.assertRaises(FileExistsError):
                fresh_report('OPS_REPORT', report)
            self.assertEqual('original evidence', report.read_text())


if __name__ == '__main__':
    unittest.main()
