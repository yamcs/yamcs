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

  async getBucket(instance: string, bucket: string): Promise<Bucket> {
    const response = await this.yamcs.doFetch(`${this.yamcs.apiUrl}/buckets/${instance}/${bucket}`);
    return await response.json() as Bucket;
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

  async getObject(instance: string, bucket: string, objectName: string) {
    return await this.yamcs.doFetch(this.getObjectURL(instance, bucket, objectName));
  }

  getObjectURL(instance: string, bucket: string, objectName: string) {
    const encodedName = this.encodeObjectName(objectName);
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

  async deleteObject(instance: string, bucket: string, objectName: string) {
    const url = `${this.yamcs.apiUrl}/buckets/${instance}/${bucket}/objects/${encodeURIComponent(objectName)}`;
    return await this.yamcs.doFetch(url, {
      method: 'DELETE',
    });
  }

  private queryString(options: { [key: string]: any; }) {
    const qs = Object.keys(options)
      .map(k => `${k}=${options[k]}`)
      .join('&');
    return qs === '' ? qs : '?' + qs;
  }

  private encodeObjectName(objectName: string) {
    return objectName.split('/')
      .map(component => encodeURIComponent(component))
      .join('/');
  }
}
