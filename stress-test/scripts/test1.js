import http from 'k6/http';
import {sleep} from 'k6';

export const options = {
    stages: [
        { duration: '10m', target: 6000 }// 10분 동안 6,000명까지 증가
    ],
};

export default function() {
    //테스트할 api
    http.get('http://43.202.182.58:8080/api/stress-test/cpu')
    sleep(1);
}