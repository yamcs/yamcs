import { BucketsWrapper } from './types/internal';
import { Bucket, CreateBucketRequest, ListObjectsOptions, ListObjectsResponse } from './types/system';
import YamcsClient from './YamcsClient';

export class StorageClient {

  constructor(private yamcs: YamcsClient) {
  }

  async createBucket(options: CreateBucketRequest) {
    const body = JSON.stringify(options);
    return await this.yamcs.doFetch(`${this.yamcs.apiUrl}/storage/buckets`, {
      body,
      method: 'POST',
    });
  }

  async getBuckets(): Promise<Bucket[]> {
    const response = await this.yamcs.doFetch(`${this.yamcs.apiUrl}/storage/buckets`);
    const wrapper = await response.json() as BucketsWrapper;
    return wrapper.buckets || [];
  }

  async getBucket(bucket: string): Promise<Bucket> {
    const response = await this.yamcs.doFetch(`${this.yamcs.apiUrl}/storage/buckets/${bucket}`);
    return await response.json() as Bucket;
  }

  async deleteBucket(bucket: string) {
    const url = `${this.yamcs.apiUrl}/storage/buckets/${bucket}`;
    return await this.yamcs.doFetch(url, {
      method: 'DELETE',
    });
  }

  async listObjects(bucket: string, options: ListObjectsOptions = {}): Promise<ListObjectsResponse> {
    const url = `${this.yamcs.apiUrl}/storage/buckets/${bucket}/objects`;
    const response = await this.yamcs.doFetch(url + this.queryString(options));
    return await response.json() as ListObjectsResponse;
  }

  async getObject(bucket: string, objectName: string) {
    return await this.yamcs.doFetch(this.getObjectURL(bucket, objectName));
  }

  getObjectURL(bucket: string, objectName: string) {
    const encodedName = this.encodeObjectName(objectName);
    return `${this.yamcs.apiUrl}/storage/buckets/${bucket}/objects/${encodedName}`;
  }

  async uploadObject(bucket: string, name: string, value: Blob | File) {
    const url = `${this.yamcs.apiUrl}/storage/buckets/${bucket}/objects`;
    const formData = new FormData();
    formData.set(name, value, name);
    return await this.yamcs.doFetch(url, {
      method: 'POST',
      body: formData,
    });
  }

  async deleteObject(bucket: string, objectName: string) {
    const url = `${this.yamcs.apiUrl}/storage/buckets/${bucket}/objects/${encodeURIComponent(objectName)}`;
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
