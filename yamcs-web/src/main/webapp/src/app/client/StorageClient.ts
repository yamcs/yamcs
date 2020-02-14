import { BucketsWrapper } from './types/internal';
import { Bucket, CreateBucketRequest, ListObjectsOptions, ListObjectsResponse } from './types/system';
import YamcsClient from './YamcsClient';

export class StorageClient {

  constructor(private yamcs: YamcsClient) {
  }

  async createBucket(instance: string, options: CreateBucketRequest) {
    const body = JSON.stringify(options);
    return await this.yamcs.doFetch(`${this.yamcs.apiUrl}/buckets/${instance}`, {
      body,
      method: 'POST',
    });
  }

  async getBuckets(instance: string): Promise<Bucket[]> {
    const response = await this.yamcs.doFetch(`${this.yamcs.apiUrl}/buckets/${instance}`);
    const wrapper = await response.json() as BucketsWrapper;
    return wrapper.buckets || [];
  }

  async deleteBucket(instance: string, name: string) {
    const url = `${this.yamcs.apiUrl}/buckets/${instance}/${name}`;
    return await this.yamcs.doFetch(url, {
      method: 'DELETE',
    });
  }

  async listObjects(instance: string, bucket: string, options: ListObjectsOptions = {}): Promise<ListObjectsResponse> {
    const url = `${this.yamcs.apiUrl}/buckets/${instance}/${bucket}/objects`;
    const response = await this.yamcs.doFetch(url + this.queryString(options));
    return await response.json() as ListObjectsResponse;
  }

  async getObject(instance: string, bucket: string, name: string) {
    return await this.yamcs.doFetch(this.getObjectURL(instance, bucket, name));
  }

  getObjectURL(instance: string, bucket: string, name: string) {
    const encodedName = encodeURIComponent(name);
    return `${this.yamcs.apiUrl}/buckets/${instance}/${bucket}/objects/${encodedName}`;
  }

  async uploadObject(instance: string, bucket: string, name: string, value: Blob | File) {
    const url = `${this.yamcs.apiUrl}/buckets/${instance}/${bucket}/objects`;
    const formData = new FormData();
    formData.set(name, value, name);
    return await this.yamcs.doFetch(url, {
      method: 'POST',
      body: formData,
    });
  }

  async deleteObject(instance: string, bucket: string, name: string) {
    const encodedName = encodeURIComponent(name);
    const url = `${this.yamcs.apiUrl}/buckets/${instance}/${bucket}/objects/${encodedName}`;
    return await this.yamcs.doFetch(url, {
      method: 'DELETE',
    });
  }

  private queryString(options: { [key: string]: any }) {
    const qs = Object.keys(options)
      .map(k => `${k}=${options[k]}`)
      .join('&');
    return qs === '' ? qs : '?' + qs;
  }
}
