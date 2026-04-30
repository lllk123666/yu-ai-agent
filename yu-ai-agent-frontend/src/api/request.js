import axios from 'axios'

const request = axios.create({
  baseURL: '/api',
  timeout: 600000,
})

export default request
