package useless;

public class test {
    
}

// 冒泡排序实现
class BubbleSort {
    public static void bubbleSort(int[] arr) {
        int n = arr.length;
        for (int i = 0; i < n - 1; i++) {
            for (int j = 0; j < n - i - 1; j++) {
                if (arr[j] > arr[j + 1]) {
                    // 交换 arr[j] 和 arr[j+1]
                    int temp = arr[j];
                    arr[j] = arr[j + 1];
                    arr[j + 1] = temp;
                }
            }
        }
    }

    public static void main(String[] args) {
        int[] arr = {64, 34, 25, 12, 22, 11, 90};
        System.out.println("排序前:");
        for (int num : arr) {
            System.out.print(num + " ");
        }

        bubbleSort(arr);

        System.out.println("\n排序后:");
        for (int num : arr) {
            System.out.print(num + " ");
        }
    }
}
